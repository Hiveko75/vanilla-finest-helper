package io.github.hiveko.vanillafinest.helper.setup;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.event.Event;

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
    private static final Identifier LATE = Identifier.fromNamespaceAndPath("vanilla-finest-helper", "after_other_mods");
    /** Message keys of Minecraft's own icon buttons on the title and pause screens. */
    private static final java.util.Set<String> BUILT_IN_ICONS = java.util.Set.of(
            "gui.friends.open", "options.language", "options.accessibility",
            "menu.reportBugs", "menu.sendFeedback", "menu.playerReporting");
    private static boolean firstStartChecked;
    private static Screen openOver;

    @Override
    public void onInitializeClient() {
        Integrations.registerWithEaseGui(SetupScreen.class);
        // the icon is placed after the other mods have added theirs (a phase after the default one)
        ScreenEvents.AFTER_INIT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ScreenEvents.AFTER_INIT.register(LATE, (mc, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof PauseScreen pause && pause.showsPauseMenu()) addIcon(screen);
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
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

    /**
     * The settings icon, right after Minecraft's own icons in their row (title screen: friends, language,
     * accessibility; pause screen: feedback, bugs, friends, reporting) and before the icons other mods
     * added; the row is then centred again. When the row also holds normal buttons (older layouts, where
     * Accessibility sits next to Quit Game), the icon only goes after the last icon and nothing is moved.
     */
    private static void addIcon(Screen screen) {
        try {
            SetupTexts.reload();
            List<AbstractWidget> widgets = Screens.getWidgets(screen);
            SpriteIconButton icon = SpriteIconButton.builder(Component.literal(SetupTexts.format("icon.tooltip")),
                            b -> SetupScreen.open(screen), true)
                    .size(20, 20).sprite(ICON, 15, 15).withTootip().build();

            // the last of Minecraft's own icons decides the row and the place
            AbstractWidget anchor = null;
            for (AbstractWidget w : widgets) {
                if (w.getWidth() != 20 || w.getHeight() != 20) continue;
                if (!(w.getMessage().getContents() instanceof TranslatableContents t) || !BUILT_IN_ICONS.contains(t.getKey())) continue;
                if (anchor == null || w.getY() == anchor.getY() && w.getX() > anchor.getX()) anchor = w;
            }
            if (anchor == null) {
                icon.setPosition(screen.width - 24, 4);
                widgets.add(icon);
                return;
            }
            int rowY = anchor.getY();
            List<AbstractWidget> row = new ArrayList<>();
            for (AbstractWidget w : widgets) if (w.getY() == rowY && w.getHeight() == 20) row.add(w);
            boolean iconsOnly = row.stream().allMatch(w -> w.getWidth() == 20);
            int ax = anchor.getX();
            if (!iconsOnly) {
                int right = ax + 20;
                for (AbstractWidget w : row) right = Math.max(right, w.getRight());
                icon.setPosition(right + 4 + 20 <= screen.width ? right + 4 : ax - 24, rowY);
                widgets.add(icon);
                return;
            }
            // make room after the anchor, put the icon there, centre the whole row
            for (AbstractWidget w : row) if (w.getX() > ax) w.setX(w.getX() + 24);
            icon.setPosition(ax + 24, rowY);
            row.add(icon);
            int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
            for (AbstractWidget w : row) {
                left = Math.min(left, w.getX());
                right = Math.max(right, w.getRight());
            }
            int shift = (screen.width - (right - left)) / 2 - left;
            for (AbstractWidget w : row) w.setX(w.getX() + shift);
            widgets.add(icon);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Setup screen: no settings icon on {} ({})", screen.getClass().getSimpleName(), e.toString());
        }
    }
}
