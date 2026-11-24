package io.github.hiveko.vanillafinest.helper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The helper's own settings, in config/vanilla-finest-helper.properties. One file for all modules:
 * every write keeps the keys of the other modules. The file is read again when it changed on disk, so
 * an edit by hand counts without a restart where the module reads its setting each time.
 */
public final class HelperSettings {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    private static final String COMMENT = "Vanilla Finest Helper settings";
    private static Properties props;
    private static long loadedStamp = Long.MIN_VALUE;

    private HelperSettings() {}

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("vanilla-finest-helper.properties");
    }

    /** The value of a key, or the fallback when the key is not in the file. */
    public static synchronized String get(String key, String fallback) {
        return load().getProperty(key, fallback);
    }

    /** True when the key is in the file (even with an empty value). */
    public static synchronized boolean has(String key) {
        return load().containsKey(key);
    }

    /** Sets a key and writes the file. */
    public static synchronized void set(String key, String value) {
        load().setProperty(key, value == null ? "" : value);
        save();
    }

    private static Properties load() {
        Path file = file();
        long stamp = stamp(file);
        if (props == null || stamp != loadedStamp) {
            Properties loaded = new Properties();
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    loaded.load(in);
                } catch (IOException e) {
                    LOGGER.warn("Could not read {}", file, e);
                }
            }
            props = loaded;
            loadedStamp = stamp;
        }
        return props;
    }

    private static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, COMMENT);
            }
            loadedStamp = stamp(file);
        } catch (IOException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static long stamp(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() ^ (Files.size(file) << 40) : -1;
        } catch (IOException e) {
            return -1;
        }
    }
}
