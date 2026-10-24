package dev.lyricsky;

import java.nio.file.Path;

/** Opens links/folders with the operating system, without relying on Minecraft internals. */
public final class Os {
    private Os() {}

    public static void open(String target) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", target);
            else if (os.contains("mac")) pb = new ProcessBuilder("open", target);
            else pb = new ProcessBuilder("xdg-open", target);
            pb.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void openPath(Path p) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) new ProcessBuilder("explorer.exe", p.toString()).start();
            else open(p.toString());
        } catch (Exception e) { e.printStackTrace(); }
    }
}
