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
 * The setup screen's texts and data files, from {@code config/vanilla-finest-helper/setup/}, read again every time
 * the setup screen opens, so the wording can be changed without recompiling.
 *
 * <ul>
 *   <li>{@code <lang>.json}: labels and hints. Defaults are in the jar ({@code assets/vanilla-finest-helper/setup/});
 *       a file with the same name in the config folder wins per text. English is the base; the game's language is
 *       laid over it (jar first, then the config folder), so a missing text falls back to English.</li>
 *   <li>{@code welcome.md} / {@code welcome.<lang>.md}: the Welcome page ({@link Markdown}). Only from the config
 *       folder: the welcome text is the modpack's own, so the jar has none. Without one the page says which file
 *       to create ({@code welcome.missing}).</li>
 * </ul>
 */
public final class SetupTexts {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    private static final String JAR_DIR = "/assets/vanilla-finest-helper/setup/";
    private static JsonObject texts = new JsonObject();
    private static boolean welcomeMissingLogged;

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

    private static String tooltipLang;
    private static JsonObject tooltipTexts = new JsonObject();
    private static long tooltipRead;

    /**
     * An Iris Shader Folder tooltip text (config/iris_shader_folder.properties, one language only) in the game's
     * language: {@code "shader.tooltips"} in {@code <lang>.json} maps the English text to the translation. Read
     * again at most every 5 seconds (as Shader Folder refreshes its own tooltips), so edits count without a
     * restart. Without a translation the text stays as it is.
     */
    public static String shaderTooltip(String text) {
        if (text == null || text.isBlank()) return text;
        try {
            String lang = language();
            if (lang.equals("en_us")) return text;
            long now = System.currentTimeMillis();
            if (!lang.equals(tooltipLang) || now - tooltipRead > 5000) {
                JsonElement e = readJson(lang + ".json").get("shader.tooltips");
                tooltipTexts = e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
                tooltipLang = lang;
                tooltipRead = now;
            }
            JsonElement t = tooltipTexts.get(text.strip());
            return t != null && t.isJsonPrimitive() ? t.getAsString() : text;
        } catch (RuntimeException | LinkageError e) {
            return text;
        }
    }

    public static JsonArray array(String key) {
        JsonElement e = texts.get(key);
        return e != null && e.isJsonArray() ? e.getAsJsonArray() : new JsonArray();
    }

    /**
     * The modpack's Welcome page in the game's language, else its default one; without either, a page that says
     * which file to create.
     */
    public static String welcome() {
        String lang = language();
        String text = readConfigText("welcome." + lang + ".md");
        if (text == null) text = readConfigText("welcome.md");
        if (text != null) return text;
        if (!welcomeMissingLogged) {
            welcomeMissingLogged = true;
            LOGGER.info("Setup screen: no welcome text; create {} (Markdown) to show one", configDir().resolve("welcome.md"));
        }
        return format("welcome.missing", shown(configDir()));
    }

    /** A folder as the player finds it: relative to the instance when it is inside, with / and a trailing /. */
    private static String shown(Path dir) {
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        Path abs = dir.toAbsolutePath().normalize();
        String text = (abs.startsWith(game) ? game.relativize(abs) : abs).toString().replace('\\', '/');
        return text.endsWith("/") ? text : text + "/";
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

    /** A text file from the config folder only, or null. */
    private static String readConfigText(String name) {
        Path file = configDir().resolve(name);
        if (!Files.isRegularFile(file)) return null;
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Setup screen: cannot read {} ({})", file, e.toString());
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
