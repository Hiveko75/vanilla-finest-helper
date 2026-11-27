package io.github.hiveko.vanillafinest.helper.setup;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's Configure button for Vanilla Finest Helper opens the setup screen (back to Mod Menu's list). */
public class ModMenuEntry implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SetupScreen::new;
    }
}
