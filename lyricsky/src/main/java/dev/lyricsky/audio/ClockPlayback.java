package dev.lyricsky.audio;

/** No audio: just a lyrics clock, for songs playing in your browser / YouTube app. */
public final class ClockPlayback implements Playback {
    private volatile boolean started, paused;
    private long baseNanos, accumMs;

    public boolean started() { return started; }

    @Override public synchronized void start() { started = true; paused = false; baseNanos = System.nanoTime(); accumMs = 0; }
    @Override public synchronized void pause() { if (started && !paused) { accumMs += (System.nanoTime() - baseNanos) / 1_000_000; paused = true; } }
    @Override public synchronized void resume() { if (started && paused) { baseNanos = System.nanoTime(); paused = false; } }
    @Override public void stop() { started = false; }
    @Override public synchronized long positionMs() {
        if (!started) return 0;
        return paused ? accumMs : accumMs + (System.nanoTime() - baseNanos) / 1_000_000;
    }
    @Override public boolean isPaused() { return paused; }
    @Override public boolean isFinished() { return false; }
}
