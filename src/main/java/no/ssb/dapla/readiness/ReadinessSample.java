package no.ssb.dapla.readiness;

public class ReadinessSample {
    private final boolean ready;
    private final long time;

    public ReadinessSample(boolean ready, long time) {
        this.ready = ready;
        this.time = time;
    }

    public boolean isReady() {
        return ready;
    }

    public long getTime() {
        return time;
    }
}
