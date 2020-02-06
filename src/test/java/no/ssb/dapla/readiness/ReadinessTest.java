package no.ssb.dapla.readiness;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadinessTest {

    private CompletableFuture<Boolean> completesSuccessfully() {
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Boolean> neverCompletes() {
        return new CompletableFuture<>();
    }

    private CompletableFuture<Boolean> negative() {
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> completesExceptionally() {
        return CompletableFuture.failedFuture(new RuntimeException());
    }

    static class ReadyOnThirdAttempt implements ReadinessCheck {

        int attempts = 0;

        @Override
        public CompletableFuture<Boolean> check() {
            if (++attempts == 3) {
                return CompletableFuture.completedFuture(true);
            }
            return CompletableFuture.completedFuture(false);
        }
    }

    @Test
    void thatReadinessWorksWhenTheFirstAttemptsFail() {
        Readiness readiness = Readiness.newBuilder(new ReadyOnThirdAttempt()).setBlockingReadinessCheckMaxAttempts(3).build();
        readiness.blockingReadinessCheck();
        assertThat(readiness.getAndKeepaliveReadinessSample().isReady()).isTrue();
    }

    @Test
    void thatReadinessWorks() {
        Readiness readiness = Readiness.newBuilder(this::completesSuccessfully).setBlockingReadinessCheckMaxAttempts(1).build();
        readiness.blockingReadinessCheck();
        assertThat(readiness.getAndKeepaliveReadinessSample().isReady()).isTrue();
    }

    @Test
    void thatReadinessWorksWhenChecksNeverCompletes() {
        Readiness readiness = Readiness.newBuilder(this::neverCompletes).setBlockingReadinessCheckMaxAttempts(1).build();
        assertThatThrownBy(readiness::blockingReadinessCheck).hasMessageContaining("All readiness checks failed");
        assertThat(readiness.getAndKeepaliveReadinessSample().isReady()).isFalse();
    }

    @Test
    void thatReadinessWorksWhenChecksAreAlwaysNegative() {
        Readiness readiness = Readiness.newBuilder(this::negative).setBlockingReadinessCheckMaxAttempts(1).build();
        assertThatThrownBy(readiness::blockingReadinessCheck).hasMessageContaining("All readiness checks failed");
        assertThat(readiness.getAndKeepaliveReadinessSample().isReady()).isFalse();
    }

    @Test
    void thatReadinessWorksWhenChecksCompleteExceptionally() {
        Readiness readiness = Readiness.newBuilder(this::completesExceptionally).setBlockingReadinessCheckMaxAttempts(1).build();
        assertThatThrownBy(readiness::blockingReadinessCheck).hasMessageContaining("All readiness checks failed");
        assertThat(readiness.getAndKeepaliveReadinessSample().isReady()).isFalse();
    }
}
