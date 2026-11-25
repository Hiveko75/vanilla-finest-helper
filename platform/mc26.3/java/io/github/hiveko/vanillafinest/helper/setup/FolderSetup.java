package io.github.hiveko.vanillafinest.helper.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.hiveko.vanillafinest.helper.folders.Folders;
import io.github.hiveko.vanillafinest.helper.homefolders.HomeFolders;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Folders page: where screenshots, Litematica schematics, Flashback replays and Axiom blueprints are
 * kept. The mods' own settings are the truth: they are read fresh every time, and only the rows the player
 * changed are written back, into each mod's own setting (in memory when the mod is running, and its file):
 * <ul>
 *   <li>Screenshot Gallery 2.x: {@code customScreenshotDir};</li>
 *   <li>Litematica: {@code customSchematicBaseDirectory} (+ its on/off switch);</li>
 *   <li>Flashback: Flashback Settings' {@code replayFolder}, or the helper's own setting without it;</li>
 *   <li>Axiom: {@code blueprint.customPath} (Axiom reads it at start-up).</li>
 * </ul>
 * An empty setting means the mod's own folder inside this instance.
 */
final class FolderSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    enum Row {
        SCREENSHOTS("screenshots", "screenshots"),
        SCHEMATICS("schematics", "schematics"),
        REPLAYS("replays", "flashback/replays"),
        BLUEPRINTS("blueprints", "axiom/blueprints");

        final String textKey;
        final String sub;

        Row(String textKey, String sub) {
            this.textKey = textKey;
            this.sub = sub;
        }
    }

    enum Preset { MODPACK, MINECRAFT, DESKTOP, DOCUMENTS }

    private FolderSetup() {}

    private static Path game() {
        return FabricLoader.getInstance().getGameDir();
    }

    private static Path config() {
        return FabricLoader.getInstance().getConfigDir();
    }

    // ---------------------------------------------------------------- which rows

    static boolean available(Row row) {
        FabricLoader loader = FabricLoader.getInstance();
        return switch (row) {
            case SCREENSHOTS -> loader.getModContainer("screenshotgallery")
                    .map(m -> m.getMetadata().getVersion().getFriendlyString())
                    .map(v -> !v.isEmpty() && Character.isDigit(v.charAt(0)) && v.charAt(0) >= '2').orElse(false);
            case SCHEMATICS -> loader.isModLoaded("litematica");
            case REPLAYS -> loader.isModLoaded("flashback")
                    && (loader.isModLoaded("flashback-settings") || Folders.FLASHBACK.active());
            case BLUEPRINTS -> loader.isModLoaded("axiom") && Folders.AXIOM.active();
        };
    }

    /** Axiom only takes a new folder after a restart. */
    static boolean needsRestart(Row row) {
        return row == Row.BLUEPRINTS;
    }

    // ---------------------------------------------------------------- reading

    /** The mod's setting now: an absolute folder, or "" for the mod's own folder in this instance. */
    static String read(Row row) {
        try {
            String value = switch (row) {
                case SCREENSHOTS -> readScreenshots();
                case SCHEMATICS -> readSchematics();
                case REPLAYS -> readReplays();
                case BLUEPRINTS -> Folders.current(Folders.AXIOM);
            };
            return value == null ? "" : value.trim();
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    /** The folder the mod uses: its setting, or its own folder in this instance. */
    static Path effective(Row row, String setting) {
        if (setting != null && !setting.isBlank()) {
            try {
                return Paths.get(setting.trim());
            } catch (InvalidPathException e) {
                // fall through to the mod's own folder
            }
        }
        return switch (row) {
            case SCREENSHOTS -> game().resolve("screenshots");
            case SCHEMATICS -> game().resolve("schematics");
            case REPLAYS -> game().resolve("flashback").resolve("replays");
            case BLUEPRINTS -> config().resolve("axiom").resolve("blueprints");
        };
    }

    private static String readScreenshots() {
        Object config = screenshotGalleryConfig();
        if (config != null) {
            try {
                Object v = config.getClass().getField("customScreenshotDir").get(config);
                return v instanceof String s ? s : "";
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall back to the file
            }
        }
        JsonObject json = readJson(config().resolve("screenshotgallery.json"));
        JsonElement v = json == null ? null : json.get("customScreenshotDir");
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    private static String readSchematics() {
        try {
            Class<?> generic = Class.forName("fi.dy.masa.litematica.config.Configs$Generic");
            Object enabled = generic.getField("CUSTOM_SCHEMATIC_BASE_DIRECTORY_ENABLED").get(null);
            Object dir = generic.getField("CUSTOM_SCHEMATIC_BASE_DIRECTORY").get(null);
            boolean on = (Boolean) enabled.getClass().getMethod("getBooleanValue").invoke(enabled);
            return on ? (String) dir.getClass().getMethod("getStringValue").invoke(dir) : "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            JsonObject json = readJson(config().resolve("litematica.json"));
            if (json == null || !json.has("Generic")) return "";
            JsonObject g = json.getAsJsonObject("Generic");
            boolean on = g.has("customSchematicBaseDirectoryEnabled") && g.get("customSchematicBaseDirectoryEnabled").getAsBoolean();
            return on && g.has("customSchematicBaseDirectory") ? g.get("customSchematicBaseDirectory").getAsString() : "";
        }
    }

    private static String readReplays() {
        if (!FabricLoader.getInstance().isModLoaded("flashback-settings")) return Folders.value(Folders.FLASHBACK);
        try {
            Object config = flashbackSettingsConfig();
            Object v = config.getClass().getField("replayFolder").get(config);
            return v instanceof String s ? s : "";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            JsonObject json = readJson(config().resolve("flashbacksettings.json"));
            JsonElement v = json == null ? null : json.get("replayFolder");
            return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
        }
    }

    // ---------------------------------------------------------------- writing

    /** Writes one folder setting ("" = the mod's own folder). The folder is created first. */
    static void write(Row row, String value) {
        String text = value == null ? "" : value.trim();
        if (!text.isEmpty()) {
            try {
                Files.createDirectories(Paths.get(text));
            } catch (InvalidPathException | IOException | SecurityException e) {
                LOGGER.warn("Setup screen: cannot create the folder \"{}\" ({}); {} not changed", text, e.toString(), row);
                return;
            }
        }
        try {
            switch (row) {
                case SCREENSHOTS -> writeScreenshots(text);
                case SCHEMATICS -> writeSchematics(text);
                case REPLAYS -> writeReplays(text);
                case BLUEPRINTS -> Folders.set(Folders.AXIOM, text);
            }
            LOGGER.info("Setup screen: {} folder is {}", row, text.isEmpty() ? "the mod's own folder in this instance" : text);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Setup screen: cannot change the {} folder ({})", row, e.toString());
        }
    }

    private static void writeScreenshots(String text) {
        Object config = screenshotGalleryConfig();
        if (config != null) {
            try {
                config.getClass().getField("customScreenshotDir").set(config, text);
                config.getClass().getMethod("save").invoke(config);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // fall back to the file
            }
        }
        Path file = config().resolve("screenshotgallery.json");
        JsonObject json = readJson(file);
        if (json == null) json = new JsonObject();
        json.addProperty("customScreenshotDir", text);
        writeJson(file, json);
    }

    private static void writeSchematics(String text) {
        try {
            Class<?> generic = Class.forName("fi.dy.masa.litematica.config.Configs$Generic");
            Object enabled = generic.getField("CUSTOM_SCHEMATIC_BASE_DIRECTORY_ENABLED").get(null);
            Object dir = generic.getField("CUSTOM_SCHEMATIC_BASE_DIRECTORY").get(null);
            enabled.getClass().getMethod("setBooleanValue", boolean.class).invoke(enabled, !text.isEmpty());
            dir.getClass().getMethod("setValueFromString", String.class).invoke(dir, text);
            Class.forName("fi.dy.masa.litematica.config.Configs").getMethod("saveToFile").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Path file = config().resolve("litematica.json");
            JsonObject json = readJson(file);
            if (json == null) json = new JsonObject();
            JsonObject g = json.has("Generic") && json.get("Generic").isJsonObject() ? json.getAsJsonObject("Generic") : new JsonObject();
            g.addProperty("customSchematicBaseDirectoryEnabled", !text.isEmpty());
            g.addProperty("customSchematicBaseDirectory", text);
            json.add("Generic", g);
            writeJson(file, json);
        }
    }

    private static void writeReplays(String text) {
        if (!FabricLoader.getInstance().isModLoaded("flashback-settings")) {
            Folders.set(Folders.FLASHBACK, text);
            return;
        }
        try {
            Object config = flashbackSettingsConfig();
            config.getClass().getField("replayFolder").set(config, text);
            Class<?> fs = Class.forName("com.flashbacksettings.FlashbackSettings");
            Object instance = fs.getMethod("getInstance").invoke(null);
            fs.getMethod("saveConfig").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            Path file = config().resolve("flashbacksettings.json");
            JsonObject json = readJson(file);
            if (json == null) json = new JsonObject();
            json.addProperty("replayFolder", text);
            writeJson(file, json);
        }
    }

    private static Object screenshotGalleryConfig() {
        try {
            return Class.forName("net.hicham.screenshotgallery.config.ScreenshotGalleryConfig").getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private static Object flashbackSettingsConfig() throws ReflectiveOperationException {
        Class<?> fs = Class.forName("com.flashbacksettings.FlashbackSettings");
        Object instance = fs.getMethod("getInstance").invoke(null);
        return fs.getMethod("getConfig").invoke(instance);
    }

    // ---------------------------------------------------------------- presets

    /** The shared base folder of a preset; null for Modpack (each mod's own folder in the instance). */
    static Path base(Preset preset) {
        return switch (preset) {
            case MODPACK -> null;
            case MINECRAFT -> HomeFolders.minecraftFolder();
            case DESKTOP -> userFolder(true).resolve("Minecraft");
            case DOCUMENTS -> userFolder(false).resolve("Minecraft");
        };
    }

    /** What a preset sets for a row ("" for Modpack). */
    static String target(Preset preset, Row row) {
        Path base = base(preset);
        if (base == null) return "";
        Path p = base;
        for (String part : row.sub.split("/")) p = p.resolve(part);
        return p.toString();
    }

    /** The preset every shown folder matches, or null when they are mixed. */
    static Preset matching(Map<Row, String> values, List<Row> rows) {
        for (Preset preset : Preset.values()) {
            boolean all = true;
            for (Row row : rows) {
                if (!same(effective(row, values.get(row)), effective(row, target(preset, row)))) {
                    all = false;
                    break;
                }
            }
            if (all) return preset;
        }
        return null;
    }

    static boolean same(Path a, Path b) {
        try {
            return a.toAbsolutePath().normalize().toString().equalsIgnoreCase(b.toAbsolutePath().normalize().toString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the folder is inside OneDrive (files are uploaded to the cloud). */
    static boolean inOneDrive(Path p) {
        if (p == null) return false;
        for (Path part : p) {
            if (part.toString().toLowerCase(Locale.ROOT).startsWith("onedrive")) return true;
        }
        return false;
    }

    /**
     * The user's real Desktop or Documents folder: Windows' known folders (follows OneDrive backup and moved
     * folders), the XDG user folders on Linux (may be localised), ~/Desktop and ~/Documents otherwise.
     */
    static Path userFolder(boolean desktop) {
        String home = System.getProperty("user.home", "");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            try {
                String p = com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(desktop
                        ? com.sun.jna.platform.win32.KnownFolders.FOLDERID_Desktop
                        : com.sun.jna.platform.win32.KnownFolders.FOLDERID_Documents);
                if (p != null && !p.isBlank()) return Paths.get(p);
            } catch (Throwable t) {
                // fall back to the user folder
            }
        } else if (!os.contains("mac")) {
            Path xdg = xdgFolder(home, desktop ? "XDG_DESKTOP_DIR" : "XDG_DOCUMENTS_DIR");
            if (xdg != null) return xdg;
        }
        return Paths.get(home, desktop ? "Desktop" : "Documents");
    }

    private static Path xdgFolder(String home, String key) {
        Path file = Paths.get(home, ".config", "user-dirs.dirs");
        if (!Files.isRegularFile(file)) return null;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (!line.startsWith(key + "=")) continue;
                String v = line.substring(key.length() + 1).replace("\"", "").replace("$HOME", home);
                return v.isBlank() ? null : Paths.get(v);
            }
        } catch (IOException | InvalidPathException e) {
            return null;
        }
        return null;
    }

    // ---------------------------------------------------------------- folder picker

    /** The last picker's callback; freed when the next picker opens (never while SDL may still call it). */
    private static SDL_DialogFileCallback lastCallback;
    private static boolean pickerOpen;

    /**
     * Opens the system's folder picker (SDL3, as Minecraft uses); {@code done} gets the folder on the main
     * thread, or is not called when the player cancels.
     */
    static void choose(Path start, Consumer<Path> done) {
        Minecraft mc = Minecraft.getInstance();
        if (pickerOpen) return; // one picker at a time
        if (lastCallback != null) {
            lastCallback.free();
            lastCallback = null;
        }
        SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            Path chosen = null;
            try {
                if (filelist != 0) {
                    long first = MemoryUtil.memGetAddress(filelist);
                    if (first != 0) chosen = Paths.get(MemoryUtil.memUTF8(first));
                }
            } catch (RuntimeException e) {
                chosen = null;
            }
            Path result = chosen;
            mc.execute(() -> {
                pickerOpen = false;
                if (result != null) done.accept(result);
            });
        });
        lastCallback = callback;
        pickerOpen = true;
        try {
            String location = start != null && Files.isDirectory(start) ? start.toString() : null;
            SDLDialog.SDL_ShowOpenFolderDialog(callback, 0L, mc.getWindow().handle(), location, false);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Setup screen: cannot open the folder picker ({})", e.toString());
            pickerOpen = false;
        }
    }

    // ---------------------------------------------------------------- JSON files

    private static JsonObject readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            Path def = config().resolve("modpack_defaults").resolve("config").resolve(file.getFileName());
            if (!Files.isRegularFile(def)) return null;
            file = def;
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }

    private static void writeJson(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Setup screen: cannot write {} ({})", file, e.toString());
        }
    }
}
