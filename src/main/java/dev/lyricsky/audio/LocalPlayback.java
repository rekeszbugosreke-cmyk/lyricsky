package dev.lyricsky.audio;

import dev.lyricsky.Config;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Plays .mp3 (via JLayer) and .wav (via the JDK) and exposes an accurate playback position. */
public final class LocalPlayback implements Playback {
    private final Path file;
    private volatile SourceDataLine line;
    private volatile boolean paused, stopped, finished;
    private Thread thread;

    public LocalPlayback(Path file) { this.file = file; }

    @Override public void start() {
        thread = new Thread(this::run, "LyricsSky-audio");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            if (file.getFileName().toString().toLowerCase().endsWith(".mp3")) playMp3(); else playWav();
            if (line != null && !stopped) line.drain();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (line != null) { line.stop(); line.close(); }
            finished = true;
        }
    }

    private void open(AudioFormat fmt) throws LineUnavailableException {
        line = AudioSystem.getSourceDataLine(fmt);
        line.open(fmt, 1 << 16);
        setVolume(Config.get().volume);
        line.start();
    }

    private void playMp3() throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Bitstream bs = new Bitstream(in);
            Decoder dec = new Decoder();
            Header h;
            while (!stopped && (h = bs.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                if (line == null) {
                    open(new AudioFormat(dec.getOutputFrequency(), 16, dec.getOutputChannels(), true, false));
                }
                short[] s = out.getBuffer();
                int len = out.getBufferLength();
                byte[] b = new byte[len * 2];
                for (int i = 0; i < len; i++) { b[2 * i] = (byte) (s[i] & 0xFF); b[2 * i + 1] = (byte) ((s[i] >> 8) & 0xFF); }
                line.write(b, 0, b.length);
                bs.closeFrame();
            }
        }
    }

    private void playWav() throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat src = raw.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                    src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try (AudioInputStream in = AudioSystem.getAudioInputStream(pcm, raw)) {
                open(pcm);
                byte[] buf = new byte[8192];
                int n;
                while (!stopped && (n = in.read(buf)) != -1) line.write(buf, 0, n);
            }
        }
    }

    @Override public void pause() { paused = true; if (line != null) line.stop(); }
    @Override public void resume() { paused = false; if (line != null) line.start(); }
    @Override public void stop() {
        stopped = true;
        SourceDataLine l = line;
        if (l != null) { l.stop(); l.flush(); }
    }
    @Override public long positionMs() { SourceDataLine l = line; return l == null ? 0 : l.getMicrosecondPosition() / 1000; }
    @Override public boolean isPaused() { return paused; }
    @Override public boolean isFinished() { return finished; }

    @Override public void setVolume(float v) {
        SourceDataLine l = line;
        if (l == null || !l.isControlSupported(FloatControl.Type.MASTER_GAIN)) return;
        FloatControl c = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
        float db = (float) (20.0 * Math.log10(Math.max(v, 0.0001f)));
        c.setValue(Math.max(c.getMinimum(), Math.min(c.getMaximum(), db)));
    }
}
