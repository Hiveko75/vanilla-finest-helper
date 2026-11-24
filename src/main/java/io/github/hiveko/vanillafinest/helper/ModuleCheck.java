package io.github.hiveko.vanillafinest.helper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Which mod versions each module was made and tested for, per Minecraft version
 * ({@value #FILE}, in platform/mc&lt;version&gt;/resources). A module is only applied when its mod is
 * installed in one of those versions; with any other version it stays off and says so in the log.
 *
 * <p>Keys are mod ids ({@code flashback}, {@code axiom}). Values are comma-separated version numbers,
 * compared with the number part of the installed version ("0.43.6" for "0.43.6-for-MC26.3"). "6.1.*"
 * accepts any 6.1.x. A key that is
 * missing means this jar has no such module.
 */
public final class ModuleCheck {
    static final String FILE = "vanilla-finest-helper.modules.properties";
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)*");
    private static Properties spec;

    private ModuleCheck() {}

    private static synchronized Properties spec() {
        if (spec == null) {
            Properties props = new Properties();
            try (InputStream in = ModuleCheck.class.getClassLoader().getResourceAsStream(FILE)) {
                if (in != null) props.load(in);
            } catch (IOException e) {
                // no list: every version-checked module stays off
            }
            spec = props;
        }
        return spec;
    }

    /** Mod id of a module key (the part before the first dot, if any). */
    public static String modId(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    /** True when this jar has the module (its key is in the list). */
    public static boolean inThisVersion(String key) {
        return spec().getProperty(key) != null;
    }

    /** True when the module's mod is installed in a version the module was made for. */
    public static boolean matches(String key) {
        String expected = spec().getProperty(key);
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId(key));
        if (expected == null || mod.isEmpty()) return false;
        String found = numberPart(mod.get().getMetadata().getVersion().getFriendlyString());
        for (String pattern : expected.split(",")) {
            pattern = pattern.trim();
            if (pattern.isEmpty()) continue;
            boolean ok = pattern.endsWith(".*")
                    ? found.startsWith(pattern.substring(0, pattern.length() - 1))
                    : found.equals(pattern);
            if (ok) return true;
        }
        return false;
    }

    /** "Flashback 0.43.6" style text of the versions a module was made for. */
    public static String expectedText(String key) {
        String expected = spec().getProperty(key, "?");
        return modName(modId(key)) + " " + expected.replace(".*", ".x").replace(",", " or").trim();
    }

    /** The installed mod's full version, or "not installed". */
    public static String installedVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("not installed");
    }

    /** The mod's display name, or its id. */
    public static String modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(c -> c.getMetadata().getName())
                .orElse(modId);
    }

    static String numberPart(String version) {
        Matcher m = NUMBER.matcher(version);
        return m.lookingAt() ? m.group() : version;
    }
}
