package dev.lyricsky;

import java.nio.file.Path;

public record Track(String title, String artist, String source, boolean youtube, long durationMs) {

    public static Track fromFile(Path p) {
        String n = p.getFileName().toString();
        n = n.substring(0, n.lastIndexOf('.'));
        String artist = "", title = n;
        int i = n.indexOf(" - ");
        if (i > 0) { artist = n.substring(0, i).trim(); title = n.substring(i + 3).trim(); }
        return new Track(title, artist, p.toString(), false, 0);
    }

    public static Track fromYouTube(String id, String rawTitle, String channel, long ms) {
        String t = rawTitle.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "").trim();
        String artist = channel.replace(" - Topic", "").trim();
        int i = t.indexOf(" - ");
        if (i > 0) { artist = t.substring(0, i).trim(); t = t.substring(i + 3).trim(); }
        return new Track(t, artist, id, true, ms);
    }
}
