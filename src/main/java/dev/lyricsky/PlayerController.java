package dev.lyricsky;

import dev.lyricsky.audio.ClockPlayback;
import dev.lyricsky.audio.LocalPlayback;
import dev.lyricsky.audio.Playback;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Holds what's currently playing. Everything the HUD needs is static + volatile. */
public final class PlayerController {
    private PlayerController() {}

    public static volatile Track track;
    public static volatile Playback playback;
    public static volatile List<Lyrics.Line> lyrics = List.of();
    public static volatile String status = "Nothing playing";
    public static volatile float anchorYaw;

    public static void play(Track t) {
        stop();
        track = t;
        lyrics = List.of();
        recenter();
        status = "Searching lyrics for " + t.title() + "...";

        CompletableFuture.supplyAsync(() -> Lyrics.fetch(t)).thenAccept(l -> {
            if (track != t) return;
            lyrics = l;
            status = l.isEmpty() ? "No synced lyrics found for " + t.title() : "";
        });

        if (t.youtube()) {
            // Companion mode: the audio plays in YouTube; the mod only keeps the lyrics clock.
            playback = new ClockPlayback();
            status = "Opened in browser. Press your Play/Pause key the moment the video starts.";
            Minecraft.getInstance().execute(() ->
                    Os.open("https://www.youtube.com/watch?v=" + t.source()));
        } else {
            LocalPlayback p = new LocalPlayback(Path.of(t.source()));
            playback = p;
            p.start();
        }
    }

    public static void togglePause() {
        Playback p = playback;
        if (p == null) return;
        if (p instanceof ClockPlayback c && !c.started()) c.start();
        else if (p.isPaused()) p.resume();
        else p.pause();
    }

    public static void stop() {
        Playback p = playback;
        if (p != null) p.stop();
        playback = null;
        track = null;
        lyrics = List.of();
        status = "Nothing playing";
    }

    public static void recenter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) anchorYaw = mc.player.getYRot();
    }

    public static void setVolume(float v) {
        Playback p = playback;
        if (p != null) p.setVolume(v);
    }
}
