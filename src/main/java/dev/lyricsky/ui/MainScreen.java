package dev.lyricsky.ui;

import dev.lyricsky.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MainScreen extends Screen {
    private enum Tab { LOCAL, YOUTUBE }
    private static Tab tab = Tab.LOCAL;
    private static int page = 0;
    private static final int ROWS = 7;
    private int seenVersion = YouTubeApi.version;

    public MainScreen() { super(Component.literal("Lyrics Sky")); }

    private static Path musicDir() { return FabricLoader.getInstance().getGameDir().resolve("lyricsky/music"); }

    private static List<Track> local() {
        try {
            Files.createDirectories(musicDir());
            try (var s = Files.list(musicDir())) {
                return s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".mp3") || n.endsWith(".wav");
                }).sorted().map(Track::fromFile).toList();
            }
        } catch (IOException e) { return List.of(); }
    }

    private static String clip(String s) { return s.length() > 52 ? s.substring(0, 51) + "…" : s; }

    @Override
    protected void init() {
        int cx = width / 2;
        addRenderableWidget(Button.builder(Component.literal("Local files"), b -> { tab = Tab.LOCAL; page = 0; rebuildWidgets(); })
                .bounds(cx - 155, 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("YouTube"), b -> { tab = Tab.YOUTUBE; page = 0; rebuildWidgets(); })
                .bounds(cx - 50, 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> minecraft.gui.setScreen(new SettingsScreen(this)))
                .bounds(cx + 55, 20, 100, 20).build());

        int y = 48;
        boolean ytUnlinked = tab == Tab.YOUTUBE && !YouTubeApi.linked();
        List<Track> list = tab == Tab.LOCAL ? local() : YouTubeApi.library;

        if (ytUnlinked) {
            addRenderableWidget(Button.builder(Component.literal("Link YouTube account"), b -> YouTubeApi.startLink())
                    .bounds(cx - 100, y + 4, 200, 20).build());
            if (YouTubeApi.userCode != null) {
                addRenderableWidget(Button.builder(Component.literal("Copy code & open Google page"), b -> {
                    minecraft.keyboardHandler.setClipboard(YouTubeApi.userCode);
                    Os.open(YouTubeApi.verifyUrl);
                }).bounds(cx - 100, y + 28, 200, 20).build());
            }
        } else {
            int from = page * ROWS, to = Math.min(list.size(), from + ROWS);
            for (int i = from; i < to; i++) {
                Track t = list.get(i);
                String label = (t.artist().isEmpty() ? "" : t.artist() + " — ") + t.title();
                addRenderableWidget(Button.builder(Component.literal(clip(label)), b -> PlayerController.play(t))
                        .bounds(cx - 155, y, 310, 20).build());
                y += 22;
            }
            if (list.isEmpty() && tab == Tab.YOUTUBE) {
                addRenderableWidget(Button.builder(Component.literal("Load my videos"), b -> YouTubeApi.refreshLibrary())
                        .bounds(cx - 100, y + 4, 200, 20).build());
            }
        }

        int by = height - 52;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; rebuildWidgets(); } })
                .bounds(cx - 155, by, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * ROWS < list.size()) { page++; rebuildWidgets(); } })
                .bounds(cx - 120, by, 30, 20).build());
        if (tab == Tab.LOCAL) {
            addRenderableWidget(Button.builder(Component.literal("Open music folder"), b -> Os.openPath(musicDir()))
                    .bounds(cx - 80, by, 235, 20).build());
        } else if (YouTubeApi.linked()) {
            addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> YouTubeApi.refreshLibrary())
                    .bounds(cx - 80, by, 115, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Unlink"), b -> { YouTubeApi.unlink(); rebuildWidgets(); })
                    .bounds(cx + 40, by, 115, 20).build());
        }

        int by2 = height - 28;
        addRenderableWidget(Button.builder(Component.literal("Play / Pause"), b -> PlayerController.togglePause())
                .bounds(cx - 155, by2, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> PlayerController.stop())
                .bounds(cx - 77, by2, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Recenter"), b -> PlayerController.recenter())
                .bounds(cx - 24, by2, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 55, by2, 100, 20).build());
    }

    @Override
    public void tick() {
        if (YouTubeApi.version != seenVersion) { seenVersion = YouTubeApi.version; rebuildWidgets(); }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        int cx = width / 2;
        if (tab == Tab.YOUTUBE) {
            String s = YouTubeApi.status;
            if (!s.isEmpty()) Draw.centered(g, font, s, cx, height - 66, 0xFFCCCCCC);
            if (YouTubeApi.userCode != null) Draw.centered(g, font, YouTubeApi.userCode, cx, 108, 0xFFFFE066);
        } else if (local().isEmpty()) {
            Draw.centered(g, font, "Drop .mp3 / .wav files into .minecraft/lyricsky/music", cx, 60, 0xFFCCCCCC);
            Draw.centered(g, font, "Name them \"Artist - Title.mp3\" so lyrics can be found", cx, 74, 0xFF999999);
        }
        Track t = PlayerController.track;
        if (t != null) Draw.centered(g, font, "Now: " + t.artist() + " — " + t.title(), cx, height - 80, 0xFF8CFF8C);
    }
}
