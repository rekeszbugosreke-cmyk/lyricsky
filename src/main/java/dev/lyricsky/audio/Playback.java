package dev.lyricsky.audio;

public interface Playback {
    void start();
    void pause();
    void resume();
    void stop();
    long positionMs();
    boolean isPaused();
    boolean isFinished();
    default void setVolume(float v) {}
}
