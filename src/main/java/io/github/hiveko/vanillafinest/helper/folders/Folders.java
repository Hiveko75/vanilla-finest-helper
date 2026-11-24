package io.github.hiveko.vanillafinest.helper.folders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.hiveko.vanillafinest.helper.HelperSettings;
import io.github.hiveko.vanillafinest.helper.ModuleCheck;
import io.github.hiveko.vanillafinest.helper.homefolders.HomeFolders;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Folders module: two more folders the helper sets, with its settings screen (Mod Menu, with Cloth Config).
 * <ul>
 *   <li>{@link #FLASHBACK}: where Flashback saves and lists replays. Flashback has no setting for it, so
 *       the helper redirects it (FlashbackMixin); the value is one line in
 *       config/vanilla-finest-helper.properties. Not used when Flashback Settings is installed, which has
 *       its own setting for this.</li>
 *   <li>{@link #AXIOM}: Axiom's blueprint folder. Axiom has its own setting for it ("customPath" in the
 *       "blueprint" part of config/axiom/axiom.json, read once at start-up; the folder must exist), so the
 *       helper only writes that setting. Axiom's main class is loaded by its own mixin plugin before other
 *       mods' mixins are prepared, so it cannot be changed with a mixin (1.1.0 crashed trying).</li>
 * </ul>
 * An empty value means the mod's own folder in the instance; any other value is used as the folder.
 * The Home folders module sets them to the shared Minecraft folder once.
 */
public final class Folders {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");

    public enum Folder {
        FLASHBACK("folder.flashback.replays", "flashback", "Flashback replays", "flashback/replays"),
        AXIOM("folder.axiom.blueprints", "axiom", "Axiom blueprints", "axiom/blueprints");

        public final String key;
        public final String module;
        public final String title;
        public final String sharedSubfolder;

        Folder(String key, String module, String title, String sharedSubfolder) {
            this.key = key;
            this.module = module;
            this.title = title;
            this.sharedSubfolder = sharedSubfolder;
        }

        /** True when the helper redirects this folder in this game (mod installed, version checked). */
        public boolean active() {
            if (!ModuleCheck.inThisVersion(module) || !ModuleCheck.matches(module)) return false;
            return this != FLASHBACK || !FabricLoader.getInstance().isModLoaded("flashback-settings");
        }

        /** The folder in the shared Minecraft folder, or null when there is no Minecraft folder. */
        public Path shared() {
            Path home = HomeFolders.minecraftFolder();
            if (home == null) return null;
            Path folder = home;
            for (String part : sharedSubfolder.split("/")) folder = folder.resolve(part);
            return folder;
        }
    }

    public static final Folder FLASHBACK = Folder.FLASHBACK;
    public static final Folder AXIOM = Folder.AXIOM;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Folders() {}

    /** The setting as written: an absolute path, or "" for the mod's own folder. */
    public static String value(Folder folder) {
        if (folder == AXIOM) return axiomCustomPath();
        return HelperSettings.get(folder.key, "").trim();
    }

    /**
     * The setting the mod uses right now, for the settings screen (the game is running then). For Axiom this
     * is the value in Axiom's settings in memory, so a change made in Axiom's own screen that Axiom has not
     * written to axiom.json yet is seen too; the file when Axiom's settings cannot be read. Not for use
     * before the game has started (it touches Axiom's main class).
     */
    public static String current(Folder folder) {
        if (folder == AXIOM) {
            String live = axiomLiveCustomPath();
            if (live != null) return live.trim();
        }
        return value(folder);
    }

    public static void set(Folder folder, String value) {
        String text = value == null ? "" : value.trim();
        if (folder == AXIOM) {
            setAxiomCustomPath(text);
            return;
        }
        HelperSettings.set(folder.key, text);
    }

    // ---- Axiom: its own "blueprint.customPath" setting in config/axiom/axiom.json ----

    private static Path axiomConfig() {
        return FabricLoader.getInstance().getConfigDir().resolve("axiom").resolve("axiom.json");
    }

    /** axiom.json, or the modpack's default for it when the instance has none yet; null when unreadable. */
    private static JsonObject readAxiomConfig() {
        Path file = axiomConfig();
        if (!Files.exists(file)) {
            file = FabricLoader.getInstance().getConfigDir().resolve("modpack_defaults").resolve("config")
                    .resolve("axiom").resolve("axiom.json");
            if (!Files.exists(file)) return new JsonObject();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Axiom blueprints: {} is not readable ({}); left as it is", file, e.toString());
            return null;
        }
    }

    private static String axiomCustomPath() {
        JsonObject json = readAxiomConfig();
        if (json == null) return "";
        JsonElement part = json.get("blueprint");
        if (part == null || !part.isJsonObject()) return "";
        JsonElement path = part.getAsJsonObject().get("customPath");
        return path != null && path.isJsonPrimitive() ? path.getAsString().trim() : "";
    }

    /** Axiom's blueprint folder setting in memory, or null when Axiom is not running or is different. */
    private static String axiomLiveCustomPath() {
        if (!FabricLoader.getInstance().isModLoaded("axiom")) return null;
        try {
            Class<?> axiom = Class.forName("com.moulberry.axiom.Axiom", false, Folders.class.getClassLoader());
            Object config = axiom.getField("configuration").get(null);
            if (config == null) return null;
            Object settings = config.getClass().getField("blueprint").get(config);
            Object path = settings.getClass().getField("customPath").get(settings);
            return path instanceof String text ? text : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    private static void setAxiomCustomPath(String text) {
        if (!text.isEmpty()) {
            try {
                Files.createDirectories(Paths.get(text));
            } catch (InvalidPathException | IOException | SecurityException e) {
                LOGGER.warn("Axiom blueprints: cannot create the folder \"{}\" ({}); not changed", text, e.toString());
                return;
            }
        }
        JsonObject json = readAxiomConfig();
        if (json == null) return;
        JsonElement part = json.get("blueprint");
        if (part != null && !part.isJsonObject()) return;
        JsonObject blueprint = part == null ? new JsonObject() : part.getAsJsonObject();
        blueprint.addProperty("customPath", text);
        if (part == null) json.add("blueprint", blueprint);
        Path file = axiomConfig();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Axiom blueprints: cannot write {} ({})", file, e.toString());
            return;
        }
        // When Axiom is already running, also change its settings in memory, so it does not write the old
        // value back when it saves its settings. The folder itself changes after a restart.
        try {
            Class<?> axiom = Class.forName("com.moulberry.axiom.Axiom", false, Folders.class.getClassLoader());
            Object config = axiom.getField("configuration").get(null);
            if (config != null) {
                Object settings = config.getClass().getField("blueprint").get(config);
                settings.getClass().getField("customPath").set(settings, text);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // Axiom not there or different: the file is enough.
        }
    }

    /** Flashback: the folder to use instead of its own, created if needed; null for its own folder. */
    public static Path override(Folder folder) {
        String text = value(folder);
        if (text.isEmpty()) return null;
        try {
            Path path = Paths.get(text);
            if (!path.isAbsolute()) return null;
            Files.createDirectories(path);
            return path;
        } catch (InvalidPathException | IOException | SecurityException e) {
            LOGGER.warn("{}: cannot use the folder \"{}\" ({}); the mod's own folder is used", folder.title, text, e.toString());
            return null;
        }
    }
}
