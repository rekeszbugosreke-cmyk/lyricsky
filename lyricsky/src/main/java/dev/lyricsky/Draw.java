package dev.lyricsky;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * All version-fragile GUI draw calls live here. If a 26.3 method name differs
 * from what's used below, this is the only file you need to touch for text drawing.
 */
public final class Draw {
    private Draw() {}

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int argb, boolean shadow) {
        g.text(f, s, x, y, argb, shadow);
    }

    public static void centered(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int argb) {
        g.text(f, s, cx - f.width(s) / 2, y, argb, true);
    }
}
