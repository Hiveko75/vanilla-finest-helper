package io.github.hiveko.vanillafinest.helper.setup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The setup screen's texts and data files. Defaults are in the jar ({@code assets/vanilla-finest-helper/setup/});
 * a file with the same name in {@code config/vanilla-finest-helper/setup/} wins, so the wording can be changed
 * without recompiling. Read again every time the setup screen opens.
 *
 * <ul>
 *   <li>{@code <lang>.json}: labels and hints. English is the base; the game's language is laid over it
 *       (jar first, then the config folder), so a missing text falls back to English.</li>
 *   <li>{@code welcome.md} / {@code welcome.<lang>.md}: the Welcome page ({@link Markdown}).</li>
 *   <li>{@code look.json}: which shaders the Look page offers for which profile.</li>
 * </ul>
 */
public final class SetupTexts {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    private static final String JAR_DIR = "/assets/vanilla-finest-helper/setup/";
    private static JsonObject texts = new JsonObject();

    private SetupTexts() {}

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("vanilla-finest-helper").resolve("setup");
    }

    /** The game's language code ("en_us", "hu_hu"). */
    public static String language() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code == null || code.isBlank() ? "en_us" : code.toLowerCase(Locale.ROOT);
    }

    /** Loads the texts again for the game's current language. */
    public static void reload() {
        String lang = language();
        JsonObject merged = new JsonObject();
        merge(merged, readJson("en_us.json"));
        if (!lang.equals("en_us")) merge(merged, readJson(lang + ".json"));
        texts = merged;
    }

    /** A text, or the key itself when it is missing. */
    public static String get(String key) {
        JsonElement e = texts.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : key;
    }

    public static boolean has(String key) {
        JsonElement e = texts.get(key);
        return e != null && e.isJsonPrimitive();
    }

    /** A text with %s filled in (in order) and {key:…} placeholders replaced by key names. */
    public static String format(String key, Object... args) {
        String text = get(key);
        if (args.length > 0) {
            try {
                text = String.format(Locale.ROOT, text, args);
            } catch (RuntimeException e) {
                // a broken placeholder in an edited file: show the text as it is
            }
        }
        return KeyNames.fill(text);
    }

    public static JsonArray array(String key) {
        JsonElement e = texts.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /** The Welcome page in the game's language, else the default one. */
    public static String welcome() {
        String lang = language();
        String text = readText("welcome." + lang + ".md");
        if (text == null) text = readText("welcome.md");
        return text == null ? "" : text;
    }

    /** look.json: the config folder's copy when there is one, else the jar's. */
    public static JsonObject look() {
        JsonObject fromConfig = readFile(configDir().resolve("look.json"));
        if (fromConfig != null) return fromConfig;
        JsonObject fromJar = readJar("look.json");
        return fromJar != null ? fromJar : new JsonObject();
    }

    // ---------------------------------------------------------------- reading

    private static JsonObject readJson(String name) {
        JsonObject result = new JsonObject();
        merge(result, readJar(name));
        merge(result, readFile(configDir().resolve(name)));
        return result;
    }

    private static void merge(JsonObject into, JsonObject from) {
        if (from == null) return;
        for (Map.Entry<String, JsonElement> e : from.entrySet()) into.add(e.getKey(), e.getValue());
    }

    private static String readText(String name) {
        Path file = configDir().resolve(name);
        if (Files.isRegularFile(file)) {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.warn("Setup screen: cannot read {} ({})", file, e.toString());
            }
        }
        try (InputStream in = SetupTexts.class.getResourceAsStream(JAR_DIR + name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject readJar(String name) {
        try (InputStream in = SetupTexts.class.getResourceAsStream(JAR_DIR + name)) {
            if (in == null) return null;
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "jar " + name);
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject readFile(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            LOGGER.warn("Setup screen: cannot read {} ({})", file, e.toString());
            return null;
        }
    }

    private static JsonObject parse(String text, String where) {
        try {
            JsonElement json = JsonParser.parseString(text);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            LOGGER.warn("Setup screen: {} is not valid JSON ({}); the defaults are used", where, e.getMessage());
            return null;
        }
    }
}
