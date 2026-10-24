package dev.lyricsky.ui;

import dev.lyricsky.Config;
import dev.lyricsky.Draw;
import dev.lyricsky.PlayerController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SettingsScreen extends Screen {
    private static final int[] COLORS = {0xFFFFFF, 0x7DF9FF, 0xFF8AD8, 0xFFE066, 0x8CFF8C, 0xFF9F43, 0xB48CFF};
    private static final String[] COLOR_NAMES = {"White", "Cyan", "Pink", "Yellow", "Green", "Orange", "Purple"};

    private final Screen parent;
    private static int page = 0;

    public SettingsScreen(Screen parent) { super(Component.literal("Lyrics Sky Settings")); this.parent = parent; }

    private static final class Slider extends AbstractSliderButton {
        private final String name, fmt; private final float min, max; private final Consumer<Float> set;
        Slider(int x, int y, int w, String name, String fmt, float min, float max, float cur, Consumer<Float> set) {
            super(x, y, w, 20, Component.empty(), (cur - min) / (max - min));
            this.name = name; this.fmt = fmt; this.min = min; this.max = max; this.set = set;
            updateMessage();
        }
        private float val() { return (float) (min + (max - min) * value); }
        @Override protected void updateMessage() { setMessage(Component.literal(name + ": " + String.format(fmt, val()))); }
        @Override protected void applyValue() { set.accept(val()); }
    }

    private void slider(int x, int y, String name, String fmt, float min, float max, Supplier<Float> get, Consumer<Float> set) {
        addRenderableWidget(new Slider(x, y, 150, name, fmt, min, max, get.get(), set));
    }

    private void toggle(int x, int y, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        addRenderableWidget(Button.builder(label(name, get.get() ? "ON" : "OFF"), b -> {
            set.accept(!get.get()); b.setMessage(label(name, get.get() ? "ON" : "OFF"));
        }).bounds(x, y, 150, 20).build());
    }

    private void colorCycle(int x, int y, String name, Supplier<Integer> get, Consumer<Integer> set) {
        addRenderableWidget(Button.builder(label(name, colorName(get.get())), b -> {
            int i = 0;
            for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == get.get()) i = k;
            set.accept(COLORS[(i + 1) % COLORS.length]);
            b.setMessage(label(name, colorName(get.get())));
        }).bounds(x, y, 150, 20).build());
    }

    private static String colorName(int c) {
        for (int k = 0; k < COLORS.length; k++) if (COLORS[k] == c) return COLOR_NAMES[k];
        return "Custom";
    }

    private static Component label(String n, String v) { return Component.literal(n + ": " + v); }

    @Override
    protected void init() {
        Config c = Config.get();
        int L = width / 2 - 155, R = width / 2 + 5, y0 = 34, step = 22;

        if (page == 0) {
            toggle(L, y0, "Lyrics", () -> c.enabled, v -> c.enabled = v);
            addRenderableWidget(Button.builder(label("Mode", c.mode.name()), b -> {
                c.mode = Config.Mode.values()[(c.mode.ordinal() + 1) % Config.Mode.values().length];
                b.setMessage(label("Mode", c.mode.name()));
            }).bounds(R, y0, 150, 20).build());
            slider(L, y0 + step, "Size", "%.1fx", 0.4f, 3f, () -> c.textScale, v -> c.textScale = v);
            slider(R, y0 + step, "Distance", "%.0f", 8f, 60f, () -> c.radius, v -> c.radius = v);
            slider(L, y0 + step * 2, "Sky height", "%.0f°", 10f, 85f, () -> c.skyElevation, v -> c.skyElevation = v);
            slider(R, y0 + step * 2, "Ring height", "%.0f°", -20f, 40f, () -> c.ringElevation, v -> c.ringElevation = v);
            slider(L, y0 + step * 3, "Line spacing", "%.0f°", 10f, 60f, () -> c.lineSpacing, v -> c.lineSpacing = v);
            slider(R, y0 + step * 3, "Orbit speed", "%.1f°/s", -10f, 10f, () -> c.orbitSpeed, v -> c.orbitSpeed = v);
            slider(L, y0 + step * 4, "Visible lines", "%.0f", 3f, 15f, () -> (float) c.visibleLines, v -> c.visibleLines = Math.round(v));
            slider(R, y0 + step * 4, "Fade", "%.2f", 0f, 1f, () -> c.fade, v -> c.fade = v);
            colorCycle(L, y0 + step * 5, "Text color", () -> c.textColor, v -> c.textColor = v);
            colorCycle(R, y0 + step * 5, "Current line", () -> c.accentColor, v -> c.accentColor = v);
            toggle(L, y0 + step * 6, "Rainbow", () -> c.rainbow, v -> c.rainbow = v);
            toggle(R, y0 + step * 6, "Shadow", () -> c.shadow, v -> c.shadow = v);
            toggle(L, y0 + step * 7, "Pop animation", () -> c.pop, v -> c.pop = v);
        } else {
            slider(L, y0, "Volume", "%.2f", 0f, 1f, () -> c.volume, v -> { c.volume = v; PlayerController.setVolume(v); });
            slider(R, y0, "Lyric offset", "%.0f ms", -3000f, 3000f, () -> (float) c.offsetMs, v -> c.offsetMs = Math.round(v));
            box(L, y0 + 44, 310, "Google OAuth client ID", c.ytClientId, v -> c.ytClientId = v.trim());
            box(L, y0 + 88, 310, "Google OAuth client secret", c.ytClientSecret, v -> c.ytClientSecret = v.trim());
            box(L, y0 + 132, 310, "Playlist ID (empty = liked videos)", c.ytPlaylistId, v -> c.ytPlaylistId = v.trim());
        }

        addRenderableWidget(Button.builder(Component.literal(page == 0 ? "Audio & YouTube >" : "< Appearance"), b -> { page = 1 - page; rebuildWidgets(); })
                .bounds(width / 2 - 155, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 5, height - 28, 150, 20).build());
    }

    private void box(int x, int y, int w, String name, String value, Consumer<String> set) {
        EditBox e = new EditBox(font, x, y, w, 20, Component.literal(name));
        e.setMaxLength(256);
        e.setValue(value);
        e.setResponder(set);
        addRenderableWidget(e);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        Draw.centered(g, font, "Lyrics Sky settings", width / 2, 14, 0xFFFFFFFF);
        if (page == 1) {
            int L = width / 2 - 155;
            Draw.text(g, font, "Google OAuth client ID", L, 34 + 34, 0xFFAAAAAA, false);
            Draw.text(g, font, "Google OAuth client secret", L, 34 + 78, 0xFFAAAAAA, false);
            Draw.text(g, font, "Playlist ID (empty = liked videos)", L, 34 + 122, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
