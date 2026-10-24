package dev.lyricsky;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** All user-customizable settings. Saved to config/lyricsky.json */
public final class Config {
    public enum Mode { SKY, AROUND, BOTH }

    public boolean enabled = true;
    public Mode mode = Mode.BOTH;
    public float textScale = 1.0f;     // size multiplier
    public float radius = 30f;         // distance of lyrics from you (blocks)
    public float skyElevation = 40f;   // degrees above horizon for the sky line
    public float ringElevation = 6f;   // degrees above horizon for the ring
    public float lineSpacing = 26f;    // degrees between lines on the ring
    public float orbitSpeed = 0f;      // degrees/second the whole thing drifts
    public int visibleLines = 7;
    public float fade = 0.8f;          // how quickly far lines fade
    public int textColor = 0xFFFFFF;
    public int accentColor = 0xFFE066;
    public boolean rainbow = false;
    public boolean shadow = true;
    public boolean pop = true;         // pop animation on new line
    public int offsetMs = 0;           // lyric sync offset
    public float volume = 0.8f;

    public String ytClientId = "";
    public String ytClientSecret = "";
    public String ytPlaylistId = "";   // empty = your liked videos

    private static Config instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("lyricsky.json"); }

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(path())) instance = GSON.fromJson(Files.readString(path()), Config.class);
        } catch (Exception ignored) { }
        if (instance == null) instance = new Config();
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(get())); } catch (Exception e) { e.printStackTrace(); }
    }
}
