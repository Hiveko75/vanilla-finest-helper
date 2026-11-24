package io.github.hiveko.vanillafinest.helper.homefolders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

import com.google.gson.Gson;
import io.github.hiveko.vanillafinest.helper.HelperSettings;
import io.github.hiveko.vanillafinest.helper.folders.Folders;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Home folders module: the first time an instance starts, points the folder settings of a few mods at
 * the player's shared Minecraft folder (the launcher's ".minecraft"), so every instance uses the same
 * screenshots, schematics and replays:
 * <ul>
 *   <li>Screenshot Gallery 2.x: its screenshot folder (where new screenshots are saved and what the
 *       gallery shows), {@code <minecraft>/screenshots};</li>
 *   <li>Litematica: its schematic folder, {@code <minecraft>/schematics};</li>
 *   <li>Flashback Settings: Flashback's replay folder, {@code <minecraft>/flashback/replays}.</li>
 * </ul>
 * It also sets the two folders of the Folders module ({@link Folders}): Flashback's replays when
 * Flashback Settings is not installed, and Axiom's blueprints ({@code <minecraft>/axiom/blueprints},
 * Axiom's own setting); see {@link #folders()}.
 * The Minecraft folder is found the same way One World Folder finds it (%APPDATA%\.minecraft on
 * Windows, ~/Library/Application Support/minecraft on macOS, ~/.minecraft on Linux).
 *
 * <p><b>Off by default</b> (since 1.1.1): folders stay in the instance (the mods' own defaults) unless
 * {@value #ENABLED_KEY}{@code =true} is set in {@code config/vanilla-finest-helper.properties}. Instances
 * where it already ran keep the folders it set.
 *
 * <p>It runs once per instance: {@value #MARKER} in the config folder records that it ran, and after
 * that the settings are never touched again, whatever the player sets. A setting the player already
 * chose (a folder that exists on this computer) is kept even on that first run. It runs before the
 * mods read their settings (Fabric's preLaunch), and only changes the settings files of mods that are
 * installed. If the Minecraft folder does not exist, nothing is changed.
 */
public class HomeFolders implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    static final String MARKER = "vanilla-finest-helper.home-folders.done";
    /** Off by default since 1.1.1: folders stay in the instance unless the player asks for the shared folder. */
    public static final String ENABLED_KEY = "homefolders.enabled";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Override
    public void onPreLaunch() {
        try {
            run(FabricLoader.getInstance());
        } catch (RuntimeException | IOException e) {
            LOGGER.warn("Home folders module: could not set the default folders", e);
        }
    }

    private static void run(FabricLoader loader) throws IOException {
        Path config = loader.getConfigDir();
        if (!Boolean.parseBoolean(HelperSettings.get(ENABLED_KEY, "false").trim())) {
            LOGGER.info("Home folders module off: folders stay in this instance (set {}=true in {} to use the shared Minecraft folder)",
                    ENABLED_KEY, HelperSettings.file().getFileName());
            return;
        }
        folders();
        Path marker = config.resolve(MARKER);
        if (Files.exists(marker)) return;

        Path home = minecraftFolder();
        StringBuilder done = new StringBuilder("The Home folders module of Vanilla Finest Helper ran once for this instance.\n"
                + "Delete this file to let it set the default folders again on the next start.\n");
        if (home == null || !Files.isDirectory(home)) {
            LOGGER.info("Home folders module: no Minecraft folder found ({}); folder settings left as they are", home);
            done.append("No Minecraft folder was found; nothing was changed.\n");
        } else {
            if (screenshotGallery(loader, config, home.resolve("screenshots"))) done.append("Screenshot Gallery: ").append(home.resolve("screenshots")).append('\n');
            if (litematica(loader, config, home.resolve("schematics"))) done.append("Litematica: ").append(home.resolve("schematics")).append('\n');
            Path replays = home.resolve("flashback").resolve("replays");
            if (flashbackSettings(loader, config, replays)) done.append("Flashback Settings: ").append(replays).append('\n');
        }
        Files.createDirectories(config);
        Files.writeString(marker, done.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Folders module ({@link Folders}): Flashback's replay folder (without Flashback Settings) and Axiom's
     * blueprint folder are set to the shared Minecraft folder once per instance, the first time the mod
     * is installed in a version the module was made for. That also happens once in instances where this
     * module ran before these two existed. A value the player already set is kept, and after that one
     * time the settings are never changed again.
     */
    private static void folders() {
        Path home = minecraftFolder();
        if (home == null || !Files.isDirectory(home)) return;
        for (Folders.Folder folder : Folders.Folder.values()) {
            if (!folder.active()) continue;
            if (folder == Folders.AXIOM) {
                axiomOnce();
                continue;
            }
            String doneKey = "homefolders." + folder.module + ".done";
            if (HelperSettings.has(doneKey)) continue;
            if (!HelperSettings.has(folder.key)) {
                Path shared = folder.shared();
                Folders.set(folder, shared.toString());
                LOGGER.info("Home folders module: {} folder is {}", folder.title, shared);
            }
            HelperSettings.set(doneKey, "true");
        }
    }

    /**
     * Axiom: its own blueprint folder setting (axiom.json, read by Axiom at start-up, after this). Kept when
     * it already names an existing folder. 1.1.0 kept the folder in the helper's settings instead
     * ("folder.axiom.blueprints"); that value is carried over when there is one.
     */
    private static void axiomOnce() {
        String doneKey = "homefolders.axiom.config.done";
        if (HelperSettings.has(doneKey)) return;
        String current = Folders.value(Folders.AXIOM);
        if (!isFolder(current)) {
            String earlier = HelperSettings.get(Folders.AXIOM.key, "").trim();
            String target = !earlier.isEmpty() ? earlier : Folders.AXIOM.shared().toString();
            Folders.set(Folders.AXIOM, target);
            LOGGER.info("Home folders module: {} folder is {}", Folders.AXIOM.title, target);
        }
        HelperSettings.set(doneKey, "true");
    }

    private static boolean isFolder(String text) {
        if (text == null || text.isBlank()) return false;
        try {
            return Files.isDirectory(Paths.get(text.trim()));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /** The launcher's Minecraft folder, as One World Folder detects it; null on an unknown system. */
    public static Path minecraftFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", "");
        try {
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                return appData != null && !appData.isBlank()
                        ? Paths.get(appData, ".minecraft")
                        : Paths.get(userHome, "AppData", "Roaming", ".minecraft");
            } else if (os.contains("mac")) {
                return Paths.get(userHome, "Library", "Application Support", "minecraft");
            } else if (os.contains("nix") || os.contains("nux")) {
                return Paths.get(userHome, ".minecraft");
            }
        } catch (InvalidPathException e) {
            // falls through
        }
        return null;
    }

    /** Screenshot Gallery 2.x: "customScreenshotDir" in screenshotgallery.json (1.x has no such setting). */
    private static boolean screenshotGallery(FabricLoader loader, Path config, Path folder) throws IOException {
        Optional<ModContainer> mod = loader.getModContainer("screenshotgallery");
        if (mod.isEmpty() || majorVersion(mod.get()) < 2) return false;
        Path file = config.resolve("screenshotgallery.json");
        JsonObject json = read(config, file);
        if (json == null || chosen(json.get("customScreenshotDir"))) return false;
        Files.createDirectories(folder);
        json.addProperty("customScreenshotDir", folder.toString());
        write(file, json);
        LOGGER.info("Home folders module: Screenshot Gallery's screenshot folder is {}", folder);
        return true;
    }

    /** Litematica: "customSchematicBaseDirectory" and its on/off switch, in the "Generic" part of litematica.json. */
    private static boolean litematica(FabricLoader loader, Path config, Path folder) throws IOException {
        if (!loader.isModLoaded("litematica")) return false;
        Path file = config.resolve("litematica.json");
        JsonObject json = read(config, file);
        if (json == null) return false;
        JsonElement part = json.get("Generic");
        if (part != null && !part.isJsonObject()) return false;
        JsonObject generic = part == null ? new JsonObject() : part.getAsJsonObject();
        JsonElement enabled = generic.get("customSchematicBaseDirectoryEnabled");
        boolean on = enabled != null && enabled.isJsonPrimitive() && enabled.getAsJsonPrimitive().isBoolean() && enabled.getAsBoolean();
        if (on && chosen(generic.get("customSchematicBaseDirectory"))) return false;
        Files.createDirectories(folder);
        generic.addProperty("customSchematicBaseDirectoryEnabled", true);
        generic.addProperty("customSchematicBaseDirectory", folder.toString());
        if (part == null) json.add("Generic", generic);
        write(file, json);
        LOGGER.info("Home folders module: Litematica's schematic folder is {}", folder);
        return true;
    }

    /** Flashback Settings: "replayFolder" in flashbacksettings.json. */
    private static boolean flashbackSettings(FabricLoader loader, Path config, Path folder) throws IOException {
        if (!loader.isModLoaded("flashback-settings")) return false;
        Path file = config.resolve("flashbacksettings.json");
        JsonObject json = read(config, file);
        if (json == null || chosen(json.get("replayFolder"))) return false;
        Files.createDirectories(folder);
        json.addProperty("replayFolder", folder.toString());
        write(file, json);
        LOGGER.info("Home folders module: Flashback's replay folder is {}", folder);
        return true;
    }

    /**
     * True when the setting already names a folder that exists on this computer: the player's own choice,
     * which is kept. Empty, missing, or a folder that is not there (a default from another computer) is not.
     */
    private static boolean chosen(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) return false;
        String text = value.getAsString().trim();
        if (text.isEmpty()) return false;
        try {
            return Files.isDirectory(Paths.get(text));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    /**
     * The settings file as JSON. If the file does not exist yet, the modpack's default for it
     * (config/modpack_defaults/config/...) is used as the start, or an empty object. Null when the file
     * exists but cannot be read as a JSON object: it is left alone.
     */
    private static JsonObject read(Path config, Path file) throws IOException {
        Path source = file;
        if (!Files.exists(source)) {
            source = config.resolve("modpack_defaults").resolve("config").resolve(file.getFileName());
            if (!Files.exists(source)) return new JsonObject();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(source, StandardCharsets.UTF_8));
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (JsonParseException | IllegalStateException e) {
            LOGGER.warn("Home folders module: {} is not readable as JSON; left as it is", source);
            return null;
        }
    }

    private static void write(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    private static int majorVersion(ModContainer mod) {
        String version = mod.getMetadata().getVersion().getFriendlyString();
        int end = 0;
        while (end < version.length() && Character.isDigit(version.charAt(end))) end++;
        try {
            return end == 0 ? 0 : Integer.parseInt(version.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
