package dev.lyricsky;

import dev.lyricsky.audio.Playback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * Draws lyrics "in the world" by projecting 3D directions (azimuth/elevation around the camera)
 * onto the screen and drawing scaled text there. This avoids the world-render API entirely,
 * so it survives rendering rewrites. Lyrics behave like a skybox: they stay put when you look
 * around, and travel with you when you walk.
 */
public final class LyricsHud {
    private LyricsHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("lyricsky", "lyrics"), LyricsHud::extract);
    }

    /** Camera basis + projection. */
    private static final class Cam {
        final float fx, fy, fz, rx, rz, ux, uy, uz, tan;
        final int w, h;

        Cam(float yawDeg, float pitchDeg, double fovDeg, int w, int h) {
            float y = (float) Math.toRadians(yawDeg), p = (float) Math.toRadians(pitchDeg);
            fx = -Mth.sin(y) * Mth.cos(p); fy = -Mth.sin(p); fz = Mth.cos(y) * Mth.cos(p);
            rx = -Mth.cos(y); rz = -Mth.sin(y);
            ux = -rz * fy; uy = rz * fx - rx * fz; uz = rx * fy;
            tan = (float) Math.tan(Math.toRadians(fovDeg) / 2.0);
            this.w = w; this.h = h;
        }

        /** returns {screenX, screenY, pixelsPerBlock} or null if behind the camera */
        float[] project(float azDeg, float elDeg, float dist) {
            float az = (float) Math.toRadians(azDeg), el = (float) Math.toRadians(elDeg);
            float dx = -Mth.sin(az) * Mth.cos(el) * dist, dy = Mth.sin(el) * dist, dz = Mth.cos(az) * Mth.cos(el) * dist;
            float xc = dx * rx + dz * rz;
            float yc = dx * ux + dy * uy + dz * uz;
            float zc = dx * fx + dy * fy + dz * fz;
            if (zc < 0.5f) return null;
            float ppb = (h / 2f) / (zc * tan);
            return new float[]{w / 2f + xc * ppb, h / 2f - yc * ppb, ppb};
        }
    }

    private static float smooth(float t) { return t * t * (3 - 2 * t); }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.get();
        if (!c.enabled || mc.player == null || mc.options.hideGui) return;
        Playback pb = PlayerController.playback;
        if (pb == null) return;

        int W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        List<Lyrics.Line> lines = PlayerController.lyrics;
        String status = PlayerController.status;
        if (lines.isEmpty() || !status.isEmpty()) {
            if (!status.isEmpty()) Draw.centered(g, mc.font, status, W / 2, 6, 0xFFCCCCCC);
            if (lines.isEmpty()) return;
        }

        long pos = pb.positionMs() + c.offsetMs;
        int idx = Lyrics.indexAt(lines, pos);
        float t = idx >= 0 ? Mth.clamp((pos - lines.get(idx).ms()) / 350f, 0f, 1f) : 0f;
        float ease = smooth(t);
        float curF = idx < 0 ? -1f : idx - 1 + ease;

        float pt = dt.getGameTimeDeltaPartialTick(false);
        float yaw = Mth.rotLerp(pt, mc.player.yRotO, mc.player.getYRot());
        float pitch = Mth.lerp(pt, mc.player.xRotO, mc.player.getXRot());
        Cam cam = new Cam(yaw, pitch, mc.options.fov().get(), W, H);

        float orbit = c.orbitSpeed * ((System.currentTimeMillis() % 3_600_000L) / 1000f);
        float baseAz = PlayerController.anchorYaw + orbit;
        float half = c.visibleLines / 2f + 0.5f;
        long now = System.currentTimeMillis();
        boolean sky = c.mode != Config.Mode.AROUND, ring = c.mode != Config.Mode.SKY;
        Font font = mc.font;

        // Ring around the player
        if (ring) {
            for (int i = idx - c.visibleLines; i <= idx + c.visibleLines; i++) {
                if (i < 0 || i >= lines.size()) continue;
                float d = i - curF, ad = Math.abs(d);
                if (ad > half) continue;
                boolean cur = i == idx;
                float alpha = cur ? 1f : Mth.clamp(1f - (ad / half) * c.fade, 0.08f, 0.85f);
                float pop = cur && c.pop ? 1f + 0.2f * (1f - ease) : 1f;
                float[] p = cam.project(baseAz + d * c.lineSpacing, c.ringElevation, c.radius);
                drawLine(g, font, cam, p, lines.get(i).text(), 1.6f * c.textScale * pop, alpha, color(c, cur, i, now));
            }
        }

        // Big lines in the sky
        if (sky) {
            int nb = c.mode == Config.Mode.SKY ? 2 : 0;
            for (int i = idx - nb; i <= idx + nb; i++) {
                if (i < 0 || i >= lines.size()) continue;
                float d = i - curF, ad = Math.abs(d);
                boolean cur = i == idx;
                float alpha = nb == 0 ? 0.3f + 0.7f * ease : Mth.clamp(1f - ad * 0.45f, 0.1f, 1f);
                float pop = cur && c.pop ? 1f + 0.2f * (1f - ease) : 1f;
                float[] p = cam.project(baseAz, c.skyElevation - d * 7f, c.radius * 1.4f);
                drawLine(g, font, cam, p, lines.get(i).text(), 2.6f * c.textScale * (cur ? pop : 0.7f), alpha, color(c, cur, i, now));
            }
        }
    }

    private static int color(Config c, boolean cur, int i, long now) {
        if (c.rainbow) return Mth.hsvToRgb(((now / 3500f) + i * 0.07f) % 1f, 0.55f, 1f);
        return cur ? c.accentColor : c.textColor;
    }

    private static void drawLine(GuiGraphicsExtractor g, Font font, Cam cam, float[] p, String text,
                                 float worldHeight, float alpha, int rgb) {
        if (p == null) return;
        float s = Mth.clamp(worldHeight * p[2] / 9f, 0.25f, 14f);
        int w = font.width(text);
        if (w * s > cam.w * 0.95f) s = cam.w * 0.95f / w;
        if (p[0] + w * s / 2 < 0 || p[0] - w * s / 2 > cam.w || p[1] < -60 || p[1] > cam.h + 60) return;
        int a = Mth.clamp((int) (alpha * 255), 8, 255);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(p[0], p[1]);
        pose.scale(s, s);
        Draw.text(g, font, text, -w / 2, -4, (a << 24) | (rgb & 0xFFFFFF), Config.get().shadow);
        pose.popMatrix();
    }
}
