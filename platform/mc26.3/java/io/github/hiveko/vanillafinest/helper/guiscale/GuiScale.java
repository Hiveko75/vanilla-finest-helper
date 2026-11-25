package io.github.hiveko.vanillafinest.helper.guiscale;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import io.github.hiveko.vanillafinest.helper.HelperSettings;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GUI scale module: an automatic GUI scale chosen from the window's width, from a table
 * ({@code guiscale.rules} in config/vanilla-finest-helper.properties, "width:scale" pairs): the highest
 * width that is not wider than the window gives the scale; none → Minecraft's own automatic scale.
 * The default table gives 640×360 GUI pixels on every 16:9 screen (1280 → 2, 1920 → 3, 2560 → 4, 3840 → 6).
 *
 * <p>{@code guiscale.mode}: {@code auto} (default) or a fixed scale (then Minecraft's own setting is used as
 * it is). Applied when the window is created or resized (start of {@code Minecraft.resizeGui}), only when its
 * size changed. The module stays off while GUI Scaler is installed; the setup screen then changes GUI
 * Scaler's own on/off setting instead.
 */
public final class GuiScale {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    public static final String MODE_KEY = "guiscale.mode";
    public static final String RULES_KEY = "guiscale.rules";
    public static final String DEFAULT_RULES =
            "640:1,720:1,800:1,1024:2,1152:2,1176:2,1280:2,1360:2,1366:2,1440:3,1512:2,1600:3,1680:3,"
                    + "1728:3,1920:3,2048:3,2560:4,2880:5,3024:5,3456:5,3840:6";
    private static int lastWidth = -1, lastHeight = -1;

    private GuiScale() {}

    /** The helper's own module runs only without GUI Scaler. */
    public static boolean ownModule() {
        return !FabricLoader.getInstance().isModLoaded("guiscaler");
    }

    /** True when the GUI scale follows the window (the helper's module, or GUI Scaler's). */
    public static boolean automatic() {
        if (ownModule()) return "auto".equals(HelperSettings.get(MODE_KEY, "auto").trim().toLowerCase(Locale.ROOT));
        Object config = GuiScaler.config();
        return config != null && GuiScaler.autoEnabled(config);
    }

    /** The scale the table gives for a window width; 0 = Minecraft's own automatic scale. */
    public static int scaleFor(int width) {
        TreeMap<Integer, Integer> rules = rules();
        var entry = rules.floorEntry(width);
        return entry == null ? 0 : entry.getValue();
    }

    /** The scale Automatic gives for this window, as a number (Minecraft's automatic resolved too). */
    public static int automaticScaleNow() {
        Minecraft mc = Minecraft.getInstance();
        int s = scaleFor(mc.getWindow().getWidth());
        return mc.getWindow().calculateScale(s, mc.isEnforceUnicode());
    }

    /** The biggest scale that fits the window. */
    public static int maxScale() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(1, mc.getWindow().calculateScale(0, mc.isEnforceUnicode()));
    }

    private static TreeMap<Integer, Integer> rules() {
        List<String> pairs = new ArrayList<>();
        if (!ownModule()) {
            Object config = GuiScaler.config();
            if (config != null) pairs.addAll(GuiScaler.rules(config));
        }
        if (pairs.isEmpty()) {
            for (String p : HelperSettings.get(RULES_KEY, DEFAULT_RULES).split(",")) pairs.add(p);
        }
        TreeMap<Integer, Integer> out = new TreeMap<>();
        for (String p : pairs) {
            String[] parts = p.trim().split(":");
            if (parts.length != 2) continue;
            try {
                out.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException e) {
                // skip a broken pair
            }
        }
        return out;
    }

    /** Mixin hook, start of Minecraft.resizeGui: sets the automatic scale when the window size changed. */
    public static void beforeResize(Minecraft mc) {
        if (!ownModule() || !automatic()) return;
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (w == lastWidth && h == lastHeight) return;
        lastWidth = w;
        lastHeight = h;
        int scale = Math.min(scaleFor(w), maxScale());
        if (mc.options.guiScale().get() != scale) {
            mc.options.guiScale().set(scale);
            LOGGER.info("GUI scale module: GUI scale {} for {}x{}", scale, w, h);
        }
    }

    /** The setup screen's choice: 0 = Automatic, otherwise a fixed scale. Applied at once. */
    public static void choose(int value) {
        Minecraft mc = Minecraft.getInstance();
        if (ownModule()) {
            HelperSettings.set(MODE_KEY, value == 0 ? "auto" : String.valueOf(value));
        } else {
            Object config = GuiScaler.config();
            if (config != null) GuiScaler.setAuto(config, value == 0);
        }
        int scale = value == 0 ? Math.min(scaleFor(mc.getWindow().getWidth()), maxScale()) : Math.min(value, maxScale());
        mc.options.guiScale().set(scale);
        mc.options.save();
        lastWidth = -1;
        mc.resizeGui();
    }

    /** GUI Scaler 1.6 (Cloth AutoConfig), by reflection. */
    private static final class GuiScaler {
        static Object holder() {
            try {
                Class<?> autoConfig = Class.forName("me.shedaniel.autoconfig.AutoConfig");
                Class<?> model = Class.forName("eu.netleak.guiscaler.config.GuiScalerConfigModel");
                return autoConfig.getMethod("getConfigHolder", Class.class).invoke(null, model);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        static Object config() {
            Object holder = holder();
            if (holder == null) return null;
            try {
                return Class.forName("me.shedaniel.autoconfig.ConfigHolder").getMethod("getConfig").invoke(holder);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        static boolean autoEnabled(Object config) {
            try {
                return config.getClass().getField("enableAutoScale").getBoolean(config);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        static List<String> rules(Object config) {
            List<String> out = new ArrayList<>();
            try {
                Object mode = config.getClass().getField("mode").get(config);
                if (mode != null && !"CUSTOM".equals(String.valueOf(mode))) return out;
                Object list = config.getClass().getField("customRules").get(config);
                if (list instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // the helper's own table
            }
            return out;
        }

        static void setAuto(Object config, boolean on) {
            try {
                config.getClass().getField("enableAutoScale").setBoolean(config, on);
                Class.forName("me.shedaniel.autoconfig.ConfigHolder").getMethod("save").invoke(holder());
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.warn("GUI scale: cannot change GUI Scaler's setting ({})", e.toString());
            }
        }
    }
}
