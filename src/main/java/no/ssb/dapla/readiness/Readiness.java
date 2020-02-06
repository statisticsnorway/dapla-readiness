package no.ssb.dapla.readiness;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Readiness {

    private static final Logger LOG = LoggerFactory.getLogger(Readiness.class);

    private final AtomicBoolean pendingReadinessCheck;
    private final AtomicReference<ReadinessSample> readinessSample;
    private final ReadinessCheck readinessCheck;
    private final int minSampleInterval;
    private final int blockingReadinessCheckMaxAttempts;

    private Readiness(ReadinessCheck readinessCheck, int minSampleInterval, int blockingReadinessCheckMaxAttempts) {
        this.pendingReadinessCheck = new AtomicBoolean(false);
        this.readinessSample = new AtomicReference<>(new ReadinessSample(false, System.currentTimeMillis()));
        this.readinessCheck = readinessCheck;
        this.minSampleInterval = minSampleInterval;
        this.blockingReadinessCheckMaxAttempts = blockingReadinessCheckMaxAttempts;
    }

    public void set(boolean ready) {
        readinessSample.set(new ReadinessSample(ready, System.currentTimeMillis()));
    }

    public ReadinessSample getAndKeepaliveReadinessSample() {
        ReadinessSample sample = readinessSample.get();
        if (System.currentTimeMillis() - sample.getTime() > minSampleInterval) {
            if (pendingReadinessCheck.compareAndSet(false, true)) { // Lock
                // asynchronously update readiness, the updated value will not be used with the current readiness check,
                // but with the first readiness check called after the lastReadySample is updated.
                readinessCheck.check().thenAccept(succeeded -> {
                    readinessSample.set(new ReadinessSample(succeeded, System.currentTimeMillis()));
                    pendingReadinessCheck.set(false);
                }).exceptionally(throwable -> {
                    readinessSample.set(new ReadinessSample(false, System.currentTimeMillis()));
                    pendingReadinessCheck.set(false);
                    if (throwable instanceof Error) {
                        throw (Error) throwable;
                    }
                    if (throwable instanceof RuntimeException) {
                        throw (RuntimeException) throwable;
                    }
                    throw new RuntimeException(throwable);
                });
            }
        }
        return sample;
    }

    public void blockingReadinessCheck() {
        boolean ready = false;
        try {
            for (int i = 1; i <= blockingReadinessCheckMaxAttempts; i++) {
                try {
                    if (readinessCheck.check().get(1, TimeUnit.SECONDS)) {
                        ready = true;
                        break;
                    }
                    LOG.debug("Readiness check was negative. Attempt: {}/{}", i, blockingReadinessCheckMaxAttempts);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // preserve
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    LOG.debug("{}. Attempt: {}/{}", e.getMessage(), i, blockingReadinessCheckMaxAttempts);
                } catch (TimeoutException e) {
                    LOG.debug("Readiness check timed out. Attempt: {}/{}", i, blockingReadinessCheckMaxAttempts);
                }
                if (i + 1 <= blockingReadinessCheckMaxAttempts) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // preserve
                        throw new RuntimeException(e);
                    }
                }
            }
        } finally {
            set(ready);
        }

        if (!ready) {
            throw new RuntimeException("All readiness checks failed");
        }
    }

    public static Builder newBuilder(ReadinessCheck readinessCheck) {
        return new Builder(readinessCheck);
    }

    public static final class Builder {
        private ReadinessCheck readinessCheck;
        private int minSampleInterval;
        private int blockingReadinessCheckMaxAttempts;

        private Builder(ReadinessCheck check) {
            setReadinessCheck(check);
        }

        public Builder setReadinessCheck(ReadinessCheck value) {
            if (value == null) {
                throw new NullPointerException();
            }
            readinessCheck = value;
            return this;
        }

        public Builder setMinSampleInterval(int value) {
            minSampleInterval = value;
            return this;
        }

        public Builder setBlockingReadinessCheckMaxAttempts(int value) {
            blockingReadinessCheckMaxAttempts = value;
            return this;
        }

        public Readiness build() {
            return new Readiness(readinessCheck, minSampleInterval, blockingReadinessCheckMaxAttempts);
        }
    }
}
