package dev.lyricsky;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Links your Google account via the OAuth "device code" flow (read-only YouTube scope)
 * and lists your liked videos or a playlist. It does NOT download or stream audio.
 */
public final class YouTubeApi {
    private YouTubeApi() {}

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";

    public static volatile String status = "";
    public static volatile String userCode;
    public static volatile String verifyUrl;
    public static volatile List<Track> library = List.of();
    public static volatile int version; // bumped whenever the UI should refresh

    private static String refreshToken, accessToken;
    private static long accessExpiresAt;

    private static Path tokenFile() { return FabricLoader.getInstance().getConfigDir().resolve("lyricsky-tokens.json"); }

    static {
        try {
            if (Files.exists(tokenFile())) {
                JsonObject o = JsonParser.parseString(Files.readString(tokenFile())).getAsJsonObject();
                if (o.has("refresh_token")) refreshToken = o.get("refresh_token").getAsString();
            }
        } catch (Exception ignored) { }
    }

    public static boolean linked() { return refreshToken != null; }

    public static void unlink() {
        refreshToken = null; accessToken = null; library = List.of(); userCode = null;
        try { Files.deleteIfExists(tokenFile()); } catch (Exception ignored) { }
        status = "Unlinked"; version++;
    }

    private static JsonObject post(String url, Map<String, String> form) throws Exception {
        StringBuilder sb = new StringBuilder();
        form.forEach((k, v) -> sb.append(sb.length() > 0 ? "&" : "")
                .append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                .append(URLEncoder.encode(v, StandardCharsets.UTF_8)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString())).build();
        return JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    private static JsonObject get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token())
                .timeout(Duration.ofSeconds(15)).GET().build();
        return JsonParser.parseString(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    /** Step 1: ask Google for a code; the user enters it at google.com/device. */
    public static void startLink() {
        Config c = Config.get();
        if (c.ytClientId.isBlank() || c.ytClientSecret.isBlank()) {
            status = "Set your Google client ID + secret in Settings > Audio & YouTube first.";
            version++;
            return;
        }
        Thread t = new Thread(() -> {
            try {
                Map<String, String> f = new LinkedHashMap<>();
                f.put("client_id", c.ytClientId); f.put("scope", SCOPE);
                JsonObject r = post("https://oauth2.googleapis.com/device/code", f);
                if (!r.has("device_code")) { status = "Google error: " + r; version++; return; }
                userCode = r.get("user_code").getAsString();
                verifyUrl = r.get("verification_url").getAsString();
                String device = r.get("device_code").getAsString();
                int interval = r.has("interval") ? r.get("interval").getAsInt() : 5;
                long deadline = System.currentTimeMillis() + r.get("expires_in").getAsLong() * 1000;
                status = "Enter the code below at " + verifyUrl;
                version++;

                while (System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);
                    Map<String, String> p = new LinkedHashMap<>();
                    p.put("client_id", c.ytClientId); p.put("client_secret", c.ytClientSecret);
                    p.put("device_code", device);
                    p.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
                    JsonObject tok = post("https://oauth2.googleapis.com/token", p);
                    if (tok.has("access_token")) {
                        accessToken = tok.get("access_token").getAsString();
                        accessExpiresAt = System.currentTimeMillis() + tok.get("expires_in").getAsLong() * 1000;
                        if (tok.has("refresh_token")) {
                            refreshToken = tok.get("refresh_token").getAsString();
                            JsonObject o = new JsonObject(); o.addProperty("refresh_token", refreshToken);
                            Files.writeString(tokenFile(), GSON.toJson(o));
                        }
                        userCode = null; status = "Linked!"; version++;
                        refreshLibrary();
                        return;
                    }
                    String err = tok.has("error") ? tok.get("error").getAsString() : "";
                    if (err.equals("slow_down")) interval += 5;
                    else if (!err.equals("authorization_pending")) { status = "Link failed: " + err; userCode = null; version++; return; }
                }
                status = "Code expired. Try again."; userCode = null; version++;
            } catch (Exception e) { status = "Link error: " + e.getMessage(); version++; }
        }, "LyricsSky-link");
        t.setDaemon(true);
        t.start();
    }

    private static String token() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < accessExpiresAt - 30_000) return accessToken;
        Config c = Config.get();
        Map<String, String> f = new LinkedHashMap<>();
        f.put("client_id", c.ytClientId); f.put("client_secret", c.ytClientSecret);
        f.put("refresh_token", refreshToken); f.put("grant_type", "refresh_token");
        JsonObject r = post("https://oauth2.googleapis.com/token", f);
        accessToken = r.get("access_token").getAsString();
        accessExpiresAt = System.currentTimeMillis() + r.get("expires_in").getAsLong() * 1000;
        return accessToken;
    }

    /** Loads liked videos (or the playlist set in Settings). */
    public static void refreshLibrary() {
        if (!linked()) return;
        status = "Loading your library..."; version++;
        Thread t = new Thread(() -> {
            try {
                String pl = Config.get().ytPlaylistId.trim();
                List<Track> out = new ArrayList<>();
                if (pl.isEmpty()) {
                    JsonObject r = get("https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&myRating=like&maxResults=50");
                    addVideos(r.getAsJsonArray("items"), out);
                } else {
                    JsonObject r = get("https://www.googleapis.com/youtube/v3/playlistItems?part=snippet&maxResults=50&playlistId="
                            + URLEncoder.encode(pl, StandardCharsets.UTF_8));
                    StringBuilder ids = new StringBuilder();
                    for (JsonElement e : r.getAsJsonArray("items")) {
                        if (ids.length() > 0) ids.append(',');
                        ids.append(e.getAsJsonObject().getAsJsonObject("snippet").getAsJsonObject("resourceId").get("videoId").getAsString());
                    }
                    JsonObject v = get("https://www.googleapis.com/youtube/v3/videos?part=snippet,contentDetails&id=" + ids);
                    addVideos(v.getAsJsonArray("items"), out);
                }
                library = List.copyOf(out);
                status = out.size() + " videos loaded"; version++;
            } catch (Exception e) { status = "Load failed: " + e.getMessage(); version++; }
        }, "LyricsSky-yt");
        t.setDaemon(true);
        t.start();
    }

    private static void addVideos(JsonArray items, List<Track> out) {
        if (items == null) return;
        for (JsonElement e : items) {
            JsonObject o = e.getAsJsonObject();
            JsonObject sn = o.getAsJsonObject("snippet");
            long ms = 0;
            try { ms = Duration.parse(o.getAsJsonObject("contentDetails").get("duration").getAsString()).toMillis(); } catch (Exception ignored) { }
            out.add(Track.fromYouTube(o.get("id").getAsString(), sn.get("title").getAsString(),
                    sn.get("channelTitle").getAsString(), ms));
        }
    }
}
