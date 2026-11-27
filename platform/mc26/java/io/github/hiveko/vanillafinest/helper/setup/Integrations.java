package io.github.hiveko.vanillafinest.helper.setup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The other mods the setup screen reads and changes. Every one is optional and reached by name (checked in
 * the 26.3 pack's versions: Language Reload 1.7.8, Multi Key Bindings 1.4.1, Voxy 0.2.20, Iris 1.11.7,
 * Iris Shader Folder 1.4.3, EaseGUI 1.0.0, Keycaps 1.0.0); when a mod is missing or different, its control
 * is greyed out or hidden.
 */
final class Integrations {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");

    private Integrations() {}

    static boolean loaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    // ---------------------------------------------------------------- languages (Language Reload)

    /** Main language first, then the extra ones (Language Reload); just the main one without it. */
    static List<String> languages() {
        List<String> out = new ArrayList<>();
        if (loaded("languagereload")) {
            try {
                Object list = Class.forName("jerozgen.languagereload.LanguageReload").getMethod("getLanguages").invoke(null);
                if (list instanceof List<?> l) for (Object o : l) if (o instanceof String s && !s.equals("*")) out.add(s);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                out.clear();
            }
        }
        if (out.isEmpty()) out.add(Minecraft.getInstance().getLanguageManager().getSelected());
        return out;
    }

    /** Sets the main language and the extra ones (Language Reload only). */
    static boolean setLanguages(String main, List<String> extra) {
        if (!loaded("languagereload")) return false;
        try {
            Class.forName("jerozgen.languagereload.LanguageReload").getMethod("setLanguage", String.class, LinkedList.class)
                    .invoke(null, main, new LinkedList<>(extra));
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Setup screen: cannot set the languages ({})", e.toString());
            return false;
        }
    }

    /** "English (US)" with the region, "Magyar" without. */
    static String languageName(String code, boolean withRegion) {
        LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(code);
        if (info == null) return code;
        return withRegion ? info.toComponent().getString() : info.name();
    }

    private static final java.util.Map<String, com.google.gson.JsonObject> LANGUAGE_FILES = new java.util.HashMap<>();

    /**
     * A text in another language than the game's ("block.minecraft.stone" in de_de = "Stein"), from that
     * language's files in the game's resources (resource packs included, the top one wins); null if missing.
     */
    static String translationIn(String code, String key) {
        com.google.gson.JsonObject merged = LANGUAGE_FILES.get(code);
        if (merged == null) {
            merged = new com.google.gson.JsonObject();
            try {
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace("lang/" + code + ".json");
                for (net.minecraft.server.packs.resources.Resource r : Minecraft.getInstance().getResourceManager().getResourceStack(id)) {
                    try (java.io.Reader reader = r.openAsReader()) {
                        com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(reader);
                        if (json.isJsonObject()) {
                            for (var e : json.getAsJsonObject().entrySet()) merged.add(e.getKey(), e.getValue());
                        }
                    } catch (Exception e) {
                        // a broken language file in a resource pack: skip it
                    }
                }
            } catch (RuntimeException e) {
                // no resources yet
            }
            LANGUAGE_FILES.put(code, merged);
        }
        com.google.gson.JsonElement v = merged.get(key);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : null;
    }

    /** The game's language code for the computer's language ("hu_hu"), or null when the game has none. */
    static String systemLanguage() {
        Locale locale = Locale.getDefault();
        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        if (lang.isEmpty()) return null;
        LanguageManager manager = Minecraft.getInstance().getLanguageManager();
        String exact = lang + "_" + locale.getCountry().toLowerCase(Locale.ROOT);
        if (manager.getLanguage(exact) != null) return exact;
        for (String code : manager.getLanguages().keySet()) {
            if (code.startsWith(lang + "_")) return code;
        }
        return null;
    }

    // ---------------------------------------------------------------- key bindings

    /** The active Multi Key Bindings profile, or null. */
    static String keyProfile() {
        if (!loaded("multi-key-bindings")) return null;
        try {
            Object name = Class.forName("us.kenny.ProfileManager").getMethod("getActiveProfileName").invoke(null);
            return name instanceof String s && !s.isBlank() ? s.replace('_', ' ') : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    static boolean keycaps() {
        if (!loaded("keycaps")) return false;
        try {
            Class.forName("io.github.hiveko.keycaps.KeycapsApi").getMethod("open", Screen.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            return false;
        }
    }

    static void openKeycaps(Screen parent) {
        try {
            Class.forName("io.github.hiveko.keycaps.KeycapsApi").getMethod("open", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Setup screen: cannot open Keycaps ({})", e.toString());
        }
    }

    // ---------------------------------------------------------------- graphics

    /** True when the game runs on Vulkan now (not the setting, which counts after a restart). */
    static boolean runningOnVulkan() {
        return Compat.runningOnVulkan();
    }

    static boolean voxyInstalled() {
        return loaded("voxy") && voxyConfig() != null;
    }

    private static Object voxyConfig() {
        try {
            return Class.forName("me.cortex.voxy.client.config.VoxyConfig").getField("CONFIG").get(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Voxy's own on/off setting ("enabled"). */
    static boolean voxyEnabled() {
        Object config = voxyConfig();
        if (config == null) return false;
        try {
            return config.getClass().getField("enabled").getBoolean(config);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Turns Voxy on or off the way its own settings page does: in a world it starts or stops at once
     * (and Iris reloads when shaders are on); the setting is saved.
     */
    static void setVoxyEnabled(boolean on) {
        Object config = voxyConfig();
        if (config == null) return;
        try {
            config.getClass().getField("enabled").setBoolean(config, on);
            boolean inSession = Class.forName("me.cortex.voxy.client.ClientSessionEvents").getField("inSession").getBoolean(null);
            Class<?> common = Class.forName("me.cortex.voxy.commonImpl.VoxyCommon");
            if (on) {
                if (inSession) common.getMethod("createInstance").invoke(null);
            } else {
                shutdownVoxyRenderer();
                common.getMethod("shutdownInstance").invoke(null);
            }
            config.getClass().getMethod("save").invoke(config);
            if (inSession && Iris.enabled()) {
                Class.forName("me.cortex.voxy.client.core.util.IrisUtil").getMethod("reload").invoke(null);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.warn("Setup screen: cannot switch Voxy {} ({})", on ? "on" : "off", e.toString());
        }
    }

    /**
     * Stops Voxy's renderer: Voxy 0.2.18 and newer keep it behind IVoxyRenderSystemHolder; older ones
     * (0.2.16 on 1.21.11) on the level renderer through IGetVoxyRenderSystem.
     */
    private static void shutdownVoxyRenderer() throws ReflectiveOperationException {
        try {
            Class<?> holderType = Class.forName("me.cortex.voxy.client.core.IVoxyRenderSystemHolder");
            Object holder = holderType.getMethod("getNullableHolder").invoke(null);
            if (holder != null) holderType.getMethod("voxy$shutdownRenderer").invoke(holder);
        } catch (ClassNotFoundException e) {
            Class<?> getter = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem");
            Object levelRenderer = net.minecraft.client.Minecraft.getInstance().levelRenderer;
            if (getter.isInstance(levelRenderer)) getter.getMethod("voxy$shutdownRenderer").invoke(levelRenderer);
        }
    }

    // ---------------------------------------------------------------- shaders (Iris)

    static final class Iris {
        private Iris() {}

        static boolean installed() {
            return loaded("iris") && config() != null;
        }

        private static Object config() {
            try {
                return Class.forName("net.irisshaders.iris.Iris").getMethod("getIrisConfig").invoke(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        static boolean enabled() {
            Object config = config();
            if (config == null) return false;
            try {
                return (Boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        /** The selected shader pack's file name, or "". */
        static String pack() {
            Object config = config();
            if (config == null) return "";
            try {
                Object name = config.getClass().getMethod("getShaderPackName").invoke(config);
                return name instanceof Optional<?> o && o.isPresent() ? String.valueOf(o.get()) : "";
            } catch (ReflectiveOperationException | RuntimeException e) {
                return "";
            }
        }

        /** Selects a shader pack (null or "" = shaders off), saves Iris's settings and reloads. */
        static void apply(String file) {
            Object config = config();
            if (config == null) return;
            try {
                boolean on = file != null && !file.isEmpty();
                if (on) config.getClass().getMethod("setShaderPackName", String.class).invoke(config, file);
                config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, on);
                config.getClass().getMethod("save").invoke(config);
                Class.forName("net.irisshaders.iris.Iris").getMethod("reload").invoke(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.warn("Setup screen: cannot change the shaders ({})", e.toString());
            }
        }

        static java.nio.file.Path folder() {
            try {
                Object p = Class.forName("net.irisshaders.iris.Iris").getMethod("getShaderpacksDirectory").invoke(null);
                if (p instanceof java.nio.file.Path path) return path;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // the game folder's shaderpacks
            }
            return FabricLoader.getInstance().getGameDir().resolve("shaderpacks");
        }

        /**
         * The shader packs Iris lists (zip files and folders with shaders inside), in Iris's order. Without
         * Iris's list: the .zip files and folders with a "shaders" folder, sorted by name.
         */
        static List<String> packs() {
            List<String> out = new ArrayList<>();
            try {
                Object manager = Class.forName("net.irisshaders.iris.Iris").getMethod("getShaderpacksDirectoryManager").invoke(null);
                Object list = manager.getClass().getMethod("enumerate").invoke(manager);
                if (list instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
                return out;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                out.clear();
            }
            java.nio.file.Path dir = folder();
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
                files.filter(p -> java.nio.file.Files.isDirectory(p.resolve("shaders"))
                                || p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
                        .map(p -> p.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(out::add);
            } catch (java.io.IOException | RuntimeException e) {
                // no shaderpacks folder
            }
            return out;
        }
    }

    // ---------------------------------------------------------------- Iris Shader Folder

    /**
     * Iris/Oculus Shader Folder (1.4.3): its filter, order, names, colours and tooltips for the shader list,
     * read through its own classes so the setup screen shows the same list as Iris.
     */
    static final class ShaderFolder {
        private static final String PACKAGE = "com.spaceagle17.iris_shader_folder.";

        private ShaderFolder() {}

        static boolean installed() {
            return loaded("iris_shader_folder");
        }

        private static Object system(String name) throws ReflectiveOperationException {
            return Class.forName(PACKAGE + "functionality." + name).getMethod("getInstance").invoke(null);
        }

        /** Its filter and its order, as in Iris's shader list. */
        static List<String> arrange(List<String> packs) {
            try {
                Object filter = system("ShaderFilterSystem");
                Method hide = filter.getClass().getMethod("shouldFilterShaderPack", String.class);
                List<String> shown = new ArrayList<>();
                for (String p : packs) if (!Boolean.TRUE.equals(hide.invoke(filter, p))) shown.add(p);
                Object reorder = system("ShaderReorderSystem");
                Object ordered = reorder.getClass().getMethod("reorderShaderPacks", List.class).invoke(reorder, shown);
                List<String> out = new ArrayList<>();
                if (ordered instanceof List<?> l) for (Object o : l) if (o instanceof String s) out.add(s);
                return out;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                LOGGER.info("Setup screen: Iris Shader Folder's order not used ({})", e.toString());
                return new ArrayList<>(packs);
            }
        }

        /** The name as Iris's list shows it: renamed, then coloured (§ codes); ".zip" is left off. */
        static String name(String pack) {
            String name = pack;
            try {
                Object rename = system("ShaderRenameSystem");
                Object renamed = rename.getClass().getMethod("renameShaderName", String.class).invoke(rename, pack);
                if (renamed instanceof String s && !s.isBlank()) name = s;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // the file name then
            }
            name = Look.withoutZip(name);
            try {
                Object recolor = system("ShaderRecolorSystem");
                Object colored = recolor.getClass().getMethod("recolorShaderName", String.class).invoke(recolor, name);
                if (colored instanceof String s && !s.isBlank()) name = s;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // without colours then
            }
            return name;
        }

        /** The shader pack's own description and the tooltip set in Shader Folder's config, or "". */
        static String tooltip(String pack) {
            try {
                Object tooltips = system("ShaderTooltipSystem");
                Object text = tooltips.getClass().getMethod("getTooltip", String.class).invoke(tooltips, pack);
                return text instanceof String s ? s.strip() : "";
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return "";
            }
        }
    }

    // ---------------------------------------------------------------- EaseGUI

    /** Keeps EaseGUI's opening animations off the setup screen (its own drawing order and fixed panel). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void registerWithEaseGui(Class<? extends Screen> screen) {
        if (!loaded("easegui")) return;
        try {
            Class<?> registry = Class.forName("net.weyne1.easegui.api.EaseGUIScreenRegistry");
            Class<?> group = Class.forName("net.weyne1.easegui.api.EaseGUIScreenGroup");
            Object other = Enum.valueOf((Class) group, "OTHER");
            String id = "vanilla-finest-helper:setup";
            Consumer<Object> off = settings -> {
                try {
                    Field enabled = settings.getClass().getField("enabled");
                    enabled.setBoolean(settings, false);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // left as EaseGUI sets it
                }
            };
            Method configurator = registry.getMethod("registerConfigurator", String.class, Consumer.class);
            configurator.invoke(null, id, off);
            registry.getMethod("register", String.class, Class.class, int.class, group).invoke(null, id, screen, 1000, other);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            LOGGER.info("Setup screen: EaseGUI not registered ({})", e.toString());
        }
    }
}
