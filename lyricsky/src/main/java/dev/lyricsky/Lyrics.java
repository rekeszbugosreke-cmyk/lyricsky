package dev.lyricsky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Synced lyrics from LRCLIB (https://lrclib.net), a free open lyrics database. */
public final class Lyrics {
    public record Line(long ms, String text) {}

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern TS = Pattern.compile("\\[(\\d+):(\\d+)(?:[.:](\\d+))?]");

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String http(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "LyricsSky/0.1 (Fabric mod)")
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return r.statusCode() == 200 ? r.body() : null;
    }

    /** Returns an empty list if nothing synced was found. Blocking: call off the main thread. */
    public static List<Line> fetch(Track t) {
        try {
            String q = "track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist());
            if (t.durationMs() > 0) q += "&duration=" + (t.durationMs() / 1000);
            String body = http("https://lrclib.net/api/get?" + q);
            if (body != null) {
                JsonElement s = JsonParser.parseString(body).getAsJsonObject().get("syncedLyrics");
                if (s != null && !s.isJsonNull()) return parse(s.getAsString());
            }
            body = http("https://lrclib.net/api/search?q=" + enc((t.artist() + " " + t.title()).trim()));
            if (body != null) {
                JsonArray arr = JsonParser.parseString(body).getAsJsonArray();
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    JsonElement s = o.get("syncedLyrics");
                    if (s != null && !s.isJsonNull()) return parse(s.getAsString());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return List.of();
    }

    public static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TS.matcher(raw);
            List<Long> times = new ArrayList<>();
            int pos = 0;
            while (m.find(pos) && m.start() == pos) {
                long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                String f = m.group(3);
                long frac = 0;
                if (f != null) frac = Long.parseLong(f) * (f.length() == 1 ? 100 : f.length() == 2 ? 10 : 1);
                times.add(min * 60_000 + sec * 1000 + frac);
                pos = m.end();
            }
            String text = raw.substring(pos).trim();
            if (times.isEmpty() || text.isEmpty()) continue;
            for (long t : times) out.add(new Line(t, text));
        }
        out.sort(Comparator.comparingLong(Line::ms));
        return List.copyOf(out);
    }

    /** Index of the last line whose timestamp is <= pos, or -1. */
    public static int indexAt(List<Line> lines, long pos) {
        int lo = 0, hi = lines.size() - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).ms() <= pos) { ans = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return ans;
    }
}
