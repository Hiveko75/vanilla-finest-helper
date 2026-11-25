package io.github.hiveko.vanillafinest.helper.setup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * {@code {key:<id>}} in the setup texts: the key's current name, in the game's language, read again every
 * time the screen is shown (so a change in Key Binds shows at once).
 * <ul>
 *   <li>a key binding's id ({@code iris.keybind.toggleShaders}): its key, with Multi Key Bindings' modifiers
 *       ("Left Ctrl + F12") and extra keys ("F12 or G") when that mod is installed;</li>
 *   <li>{@code <mod>:<hotkey>} ({@code minihud:inventoryPreview}): a malilib hotkey.</li>
 * </ul>
 * Unbound or unknown: the "not set" text.
 */
public final class KeyNames {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{key:([^}]+)}");

    private KeyNames() {}

    /** The text with every {key:…} replaced by the key's name. */
    public static String fill(String text) {
        if (text == null || !text.contains("{key:")) return text;
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(name(m.group(1).trim())));
        m.appendTail(out);
        return out.toString();
    }

    /** The current name of a key binding or malilib hotkey. */
    public static String name(String id) {
        try {
            String name = id.contains(":") ? malilib(id) : keyMapping(id);
            if (name != null && !name.isBlank()) return name;
        } catch (Throwable t) {
            // a mod changed: show "not set" rather than failing the screen
        }
        return SetupTexts.get("key.notset");
    }

    private static String keyMapping(String id) {
        KeyMapping mapping = null;
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (k.getName().equals(id)) {
                mapping = k;
                break;
            }
        }
        if (mapping == null) return null;
        List<String> names = new ArrayList<>();
        Component base = mapping.isUnbound() ? null : mapping.getTranslatedKeyMessage();
        if (FabricLoader.getInstance().isModLoaded("multi-key-bindings")) {
            if (base != null) {
                Component withModifiers = Mkb.withModifiers(id, base);
                names.add((withModifiers != null ? withModifiers : base).getString());
            }
            names.addAll(Mkb.extraKeys(id));
        } else if (base != null) {
            names.add(base.getString());
        }
        return names.isEmpty() ? null : String.join(SetupTexts.get("key.or"), names);
    }

    /** Multi Key Bindings 1.4.1, by reflection (it stays optional). */
    private static final class Mkb {
        private static Method getDisplayName, getKeyBindings, isUnbound, getKey;

        static Component withModifiers(String action, Component base) {
            try {
                if (getDisplayName == null) {
                    getDisplayName = Class.forName("us.kenny.ModifierManager").getMethod("getDisplayName", String.class, Component.class);
                }
                Object result = getDisplayName.invoke(null, action, base);
                return result instanceof Component c ? c : null;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                return null;
            }
        }

        static List<String> extraKeys(String action) {
            List<String> out = new ArrayList<>();
            try {
                if (getKeyBindings == null) {
                    getKeyBindings = Class.forName("us.kenny.MultiKeyBindingManager").getMethod("getKeyBindings", String.class);
                    Class<?> binding = Class.forName("us.kenny.core.MultiKeyBinding");
                    isUnbound = binding.getMethod("isUnbound");
                    getKey = binding.getMethod("getKey");
                }
                Object result = getKeyBindings.invoke(null, action);
                if (result instanceof Collection<?> list) {
                    for (Object b : list) {
                        if ((Boolean) isUnbound.invoke(b)) continue;
                        Object key = getKey.invoke(b);
                        if (key instanceof InputConstants.Key k) out.add(k.getDisplayName().getString());
                    }
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                // no extra keys
            }
            return out;
        }
    }

    /** malilib hotkeys ("minihud:inventoryPreview"), by reflection. */
    private static String malilib(String id) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("malilib")) return null;
        int colon = id.indexOf(':');
        String mod = id.substring(0, colon).toLowerCase(Locale.ROOT).replace(" ", "");
        String hotkeyName = id.substring(colon + 1);

        Object manager = Class.forName("fi.dy.masa.malilib.event.InputEventHandler").getMethod("getKeybindManager").invoke(null);
        Method getCategories = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybindManager").getMethod("getKeybindCategories");
        Class<?> category = Class.forName("fi.dy.masa.malilib.hotkeys.KeybindCategory");
        Method getModName = category.getMethod("getModName");
        Method getHotkeys = category.getMethod("getHotkeys");
        Method getName = Class.forName("fi.dy.masa.malilib.config.IConfigBase").getMethod("getName");
        Method getKeybind = Class.forName("fi.dy.masa.malilib.hotkeys.IHotkey").getMethod("getKeybind");
        Method getKeys = Class.forName("fi.dy.masa.malilib.hotkeys.IKeybind").getMethod("getKeys");

        for (Object cat : (List<?>) getCategories.invoke(manager)) {
            String modName = String.valueOf(getModName.invoke(cat)).toLowerCase(Locale.ROOT).replace(" ", "");
            if (!modName.equals(mod)) continue;
            for (Object hotkey : (List<?>) getHotkeys.invoke(cat)) {
                if (!hotkeyName.equals(getName.invoke(hotkey))) continue;
                List<?> keys = (List<?>) getKeys.invoke(getKeybind.invoke(hotkey));
                if (keys == null || keys.isEmpty()) return null;
                List<String> names = new ArrayList<>();
                for (Object k : keys) {
                    // malilib 26.3 keeps scan codes (like Minecraft's key values); mouse buttons as button - 100
                    int code = ((Number) k).intValue();
                    InputConstants.Key key = code < 0
                            ? InputConstants.Type.MOUSE.getOrCreate(code + 100)
                            : InputConstants.Type.KEYBOARD.getOrCreate(code);
                    names.add(key.getDisplayName().getString());
                }
                return String.join(" + ", names);
            }
        }
        return null;
    }
}
