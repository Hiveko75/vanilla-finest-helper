package io.github.hiveko.vanillafinest.helper.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.hiveko.vanillafinest.helper.setup.FolderSetup.Row;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pointers in this instance when a folder is kept elsewhere: when screenshots, schematics, replays or blueprints
 * go to a folder outside the instance (the Minecraft, Desktop or Documents preset, or a folder set in the mod's
 * own settings), a shortcut is written where the files would normally be, so someone opening the instance's
 * folder finds where they went. Example: {@code <instance>/screenshots/Screenshots are in Documents - Minecraft -
 * screenshots.url}.
 *
 * <ul>
 *   <li>a shortcut that opens the folder: Windows {@code .url} (Internet Shortcut with a {@code file:} address),
 *       macOS {@code .webloc}, Linux {@code .desktop} ({@code Type=Link});</li>
 *   <li>and a {@code .txt} with the full path, which works everywhere.</li>
 * </ul>
 *
 * Never a real folder link (junction / symlink): exporting or backing up the instance would then copy the
 * shared files too. The pointers are made from the mods' real settings (read fresh), never from what the helper
 * wrote last; a setting inside the instance (or empty) removes them. Only the helper's own files are touched:
 * they carry {@link #MARKER}.
 *
 * Checked at start-up, when the setup screen applies its folders and when a folder mod's own settings screen
 * closes ({@link #watch}).
 */
final class Pointers {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    static final String MARKER = "vanilla-finest-helper-pointer";
    private static final String[] EXTENSIONS = {".url", ".webloc", ".desktop", ".txt"};

    /** The folder mods' settings screens: when one closes, the pointers are checked (on the next tick). */
    private static final Set<String> SETTINGS_SCREENS = Set.of(
            "fi.dy.masa.litematica.gui.GuiConfigs",                    // Litematica 0.29 (malilib)
            "com.flashbacksettings.client.FlashbackSettingsScreen");   // Flashback Settings 1.6
    /** Screenshot Gallery 2.x has no screen of its own: its settings are a Cloth Config screen. */
    private static final String CLOTH_SCREENS = "me.shedaniel.clothconfig2.gui.";

    private Pointers() {}

    static boolean watch(String screenClass) {
        return SETTINGS_SCREENS.contains(screenClass) || screenClass.startsWith(CLOTH_SCREENS);
    }

    /** Writes, replaces or removes the pointer of every folder row. */
    static void update() {
        Path game = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        for (Row row : Row.values()) {
            try {
                Path own = FolderSetup.effective(row, "").toAbsolutePath().normalize();
                Path real = null;
                if (FolderSetup.available(row)) {
                    String setting = FolderSetup.read(row);
                    Path p = FolderSetup.effective(row, setting).toAbsolutePath().normalize();
                    if (!p.startsWith(game)) real = p; // kept outside this instance
                }
                update(row, own, real);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.warn("Folder pointers: {} not checked ({})", row, e.toString());
            }
        }
    }

    /** {@code real} = the folder outside the instance, or null when the files are in the instance's own folder. */
    private static void update(Row row, Path own, Path real) throws RuntimeException {
        Set<String> keep = new HashSet<>();
        if (real != null) {
            String base = fileName(SetupTexts.format("pointer.name", label(row), place(real)));
            List<String[]> files = new ArrayList<>();
            files.add(new String[]{base + shortcutExtension(), shortcut(row, real)});
            files.add(new String[]{base + ".txt", text(row, real)});
            try {
                Files.createDirectories(own);
                for (String[] f : files) {
                    Path file = own.resolve(f[0]);
                    keep.add(f[0]);
                    if (Files.isRegularFile(file) && Files.readString(file, StandardCharsets.UTF_8).equals(f[1])) continue;
                    if (Files.exists(file) && !ours(file)) continue; // a file of the player's with this name
                    Files.writeString(file, f[1], StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                LOGGER.warn("Folder pointers: cannot write in {} ({})", own, e.toString());
            }
        }
        removeOthers(own, keep);
    }

    /** Removes the helper's own pointer files in the folder except the ones just written. */
    private static void removeOthers(Path own, Set<String> keep) {
        if (!Files.isDirectory(own)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(own)) {
            for (Path f : files) {
                String name = f.getFileName().toString();
                if (keep.contains(name) || !hasPointerExtension(name) || !Files.isRegularFile(f)) continue;
                if (ours(f)) Files.deleteIfExists(f);
            }
        } catch (IOException e) {
            LOGGER.warn("Folder pointers: cannot tidy {} ({})", own, e.toString());
        }
    }

    private static boolean hasPointerExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : EXTENSIONS) if (lower.endsWith(ext)) return true;
        return false;
    }

    /** True for the helper's own pointer files (small, with the marker inside). */
    private static boolean ours(Path file) {
        try {
            if (Files.size(file) > 8192) return false;
            return Files.readString(file, StandardCharsets.UTF_8).contains(MARKER);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- contents

    private static String label(Row row) {
        return SetupTexts.format("folders.row." + row.textKey);
    }

    /** The last three parts of the path ("Documents - Minecraft - screenshots"), for the file name. */
    private static String place(Path real) {
        List<String> parts = new ArrayList<>();
        for (Path p = real; p != null && parts.size() < 3; p = p.getParent()) {
            Path name = p.getFileName();
            if (name == null) break;
            parts.add(0, name.toString());
        }
        return parts.isEmpty() ? real.toString() : String.join(" - ", parts);
    }

    /** Characters a file name cannot have on Windows, macOS or Linux are left out. */
    private static String fileName(String name) {
        String clean = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll(" +", " ").strip();
        while (clean.endsWith(".")) clean = clean.substring(0, clean.length() - 1).strip();
        if (clean.length() > 150) clean = clean.substring(0, 150).strip();
        return clean.isEmpty() ? "Folder" : clean;
    }

    private static String os() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private static String shortcutExtension() {
        String os = os();
        if (os.contains("win")) return ".url";
        if (os.contains("mac")) return ".webloc";
        return ".desktop";
    }

    private static String uri(Path real) {
        String uri = real.toUri().toASCIIString(); // percent-encoded: spaces and accented letters work
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static String shortcut(Row row, Path real) {
        String os = os();
        String title = SetupTexts.format("pointer.name", label(row), real.toString());
        if (os.contains("win")) {
            return "[InternetShortcut]\r\nURL=" + uri(real) + "\r\n; " + MARKER + "\r\n";
        }
        if (os.contains("mac")) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                    + "<!-- " + MARKER + " -->\n"
                    + "<plist version=\"1.0\">\n<dict>\n\t<key>URL</key>\n\t<string>" + xml(uri(real)) + "</string>\n</dict>\n</plist>\n";
        }
        return "[Desktop Entry]\nType=Link\nName=" + title.replace("\n", " ") + "\nIcon=folder\nURL=" + uri(real)
                + "\n# " + MARKER + "\n";
    }

    private static String text(Row row, Path real) {
        String nl = os().contains("win") ? "\r\n" : "\n";
        return SetupTexts.format("pointer.text", label(row)) + nl + real + nl + nl
                + SetupTexts.format("pointer.note") + nl + "(" + MARKER + ")" + nl;
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
