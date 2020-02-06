package no.ssb.dapla.readiness;

import java.util.concurrent.CompletableFuture;

public interface ReadinessCheck {

    CompletableFuture<Boolean> check();
}
