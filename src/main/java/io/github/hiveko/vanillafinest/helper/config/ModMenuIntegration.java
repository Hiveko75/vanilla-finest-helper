package io.github.hiveko.vanillafinest.helper.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Mod Menu's Configure button for the helper. The settings screen needs Cloth Config; without it there
 * is no button, and the settings stay editable in config/vanilla-finest-helper.properties.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config") || !HelperConfigScreen.hasOptions()) {
            return parent -> null;
        }
        return HelperConfigScreen::create;
    }
}
