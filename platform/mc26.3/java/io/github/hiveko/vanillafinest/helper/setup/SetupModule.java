package io.github.hiveko.vanillafinest.helper.setup;

import java.util.ArrayList;
import java.util.List;

import io.github.hiveko.vanillafinest.helper.HelperSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Setup screen module (26.3): opens the setup screen on the first start (once per instance, recorded as
 * {@value #DONE_KEY} in config/vanilla-finest-helper.properties) and adds a settings icon to the icon row of
 * the title screen (next to Accessibility) and of the pause screen. Mod Menu's Configure button opens it too
 * ({@link ModMenuEntry}).
 */
public class SetupModule implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    public static final String DONE_KEY = "setup.done";
    private static final String LANGUAGE_CHECKED_KEY = "setup.language.checked";
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("vanilla-finest-helper", "icon/setup");
    private static boolean firstStartChecked;
    private static Screen openOver;

    @Override
    public void onInitializeClient() {
        Integrations.registerWithEaseGui(SetupScreen.class);
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen pause && pause.showsPauseMenu()) addIcon(screen);
            if (screen instanceof TitleScreen && !firstStartChecked) {
                firstStartChecked = true;
                if (!"true".equals(HelperSettings.get(DONE_KEY, "false").trim())) openOver = screen;
            }
        });
        // opened from the tick after the title screen is shown, not from inside its init
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (openOver == null) return;
            Screen over = openOver;
            openOver = null;
            if (mc.gui.screen() != over) return;
            addSystemLanguageOnce();
            SetupScreen.open(over);
        });
    }

    /**
     * First start, with Language Reload: the computer's language (when it is not English and the game has it)
     * is added as a second language, so item search works in it too. Menus stay in the first language.
     */
    private static void addSystemLanguageOnce() {
        if (HelperSettings.has(LANGUAGE_CHECKED_KEY)) return;
        HelperSettings.set(LANGUAGE_CHECKED_KEY, "true");
        if (!Integrations.loaded("languagereload")) return;
        String system = Integrations.systemLanguage();
        if (system == null || system.startsWith("en_")) return;
        List<String> langs = Integrations.languages();
        if (langs.contains(system)) return;
        List<String> extra = new ArrayList<>(langs.subList(1, langs.size()));
        extra.add(0, system);
        if (Integrations.setLanguages(langs.get(0), extra)) {
            HelperSettings.set("setup.language.added", system);
            LOGGER.info("Setup screen: {} added as a second language (the computer's language)", system);
        }
    }

    /** The settings icon, after the other icons of the row (title: next to Accessibility; pause: centred row). */
    private static void addIcon(Screen screen) {
        try {
            SetupTexts.reload();
            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            SpriteIconButton icon = SpriteIconButton.builder(Component.literal(SetupTexts.format("icon.tooltip")),
                            b -> SetupScreen.open(screen), true)
                    .size(20, 20).sprite(ICON, 15, 15).withTootip().build();

            int rowY = Integer.MIN_VALUE;
            if (screen instanceof TitleScreen) {
                for (AbstractWidget w : widgets) {
                    if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("options.accessibility")) {
                        rowY = w.getY();
                        break;
                    }
                }
            } else {
                // the pause screen's row of icon buttons (report bugs, social, friends, …)
                int best = 0;
                for (AbstractWidget w : widgets) {
                    if (!(w instanceof SpriteIconButton) || w.getWidth() != 20) continue;
                    int count = 0;
                    for (AbstractWidget o : widgets) if (o instanceof SpriteIconButton && o.getY() == w.getY()) count++;
                    if (count > best) {
                        best = count;
                        rowY = w.getY();
                    }
                }
                if (rowY != Integer.MIN_VALUE) {
                    // keep the row centred: move it half an icon to the left
                    for (AbstractWidget w : widgets) if (w.getY() == rowY && w.getHeight() == 20) w.setX(w.getX() - 12);
                }
            }

            if (rowY == Integer.MIN_VALUE) {
                icon.setPosition(screen.width - 24, 4);
            } else {
                int right = Integer.MIN_VALUE, left = Integer.MAX_VALUE;
                for (AbstractWidget w : widgets) {
                    if (w.getY() != rowY || w.getHeight() != 20) continue;
                    right = Math.max(right, w.getRight());
                    left = Math.min(left, w.getX());
                }
                int x = right + 4;
                if (x + 20 > screen.width) x = left - 24;
                icon.setPosition(x, rowY);
            }
            widgets.add(icon);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Setup screen: no settings icon on {} ({})", screen.getClass().getSimpleName(), e.toString());
        }
    }
}
