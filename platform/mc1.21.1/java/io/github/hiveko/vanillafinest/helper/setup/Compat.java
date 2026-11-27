package io.github.hiveko.vanillafinest.helper.setup;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.Options;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The parts of the setup screen that differ by Minecraft version: Minecraft 1.21.1: GLFW windowing with
 * LWJGL's tinyfd as the folder picker (bundled with Minecraft), OpenGL only, screens through
 * Minecraft.setScreen, the drawing calls without render pipelines and tooltips through the screen.
 */
final class Compat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");
    private static volatile boolean pickerOpen;

    private Compat() {}

    /** A keyboard key as malilib keeps it: GLFW key codes, like Minecraft's own key values here. */
    static InputConstants.Key keyboard(int code) {
        return InputConstants.Type.KEYSYM.getOrCreate(code);
    }

    /**
     * Opens the system's folder picker; {@code done} gets the folder on the main thread, or is not called
     * when the player cancels. tinyfd waits until the picker closes, so it runs on its own thread and the
     * game keeps drawing.
     */
    static void choose(Path start, Consumer<Path> done) {
        if (pickerOpen) return; // one picker at a time
        pickerOpen = true;
        Minecraft mc = Minecraft.getInstance();
        String location = start != null && Files.isDirectory(start) ? withSeparator(start.toString()) : null;
        Thread t = new Thread(() -> {
            Path chosen = null;
            try {
                String picked = TinyFileDialogs.tinyfd_selectFolderDialog(SetupTexts.format("folders.picker.title"), location);
                if (picked != null && !picked.isBlank()) chosen = Paths.get(picked);
            } catch (RuntimeException | LinkageError e) {
                LOGGER.warn("Setup screen: cannot open the folder picker ({})", e.toString());
            }
            Path result = chosen;
            mc.execute(() -> {
                pickerOpen = false;
                if (result != null) done.accept(result);
            });
        }, "Vanilla Finest Helper folder picker");
        t.setDaemon(true);
        t.start();
    }

    /** tinyfd opens a folder's contents only when its path ends with a separator. */
    private static String withSeparator(String path) {
        return path.endsWith("/") || path.endsWith("\\") ? path : path + java.io.File.separator;
    }

    /** Shows a screen (26.1.2 and older: {@code Minecraft.setScreen}). */
    static void setScreen(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    /** The screen shown now. */
    static Screen screen() {
        return Minecraft.getInstance().screen;
    }

    /** No choice of graphics API before 26.2 (OpenGL only): a button that only shows it. */
    static AbstractWidget graphicsApiButton(Options options, int x, int y, int width) {
        Button b = Button.builder(Component.literal(SetupTexts.format("settings.graphics_api", graphicsApiName(options))),
                btn -> {}).bounds(x, y, width, 20).build();
        b.active = false;
        return b;
    }

    static String graphicsApiName(Options options) {
        return "OpenGL";
    }

    static boolean runningOnVulkan() {
        return false;
    }

    /** Where the render distance slider's knob sits for this many chunks (2 to 32). */
    static double renderDistancePosition(Options options, int chunks) {
        return (chunks - 2) / 30.0;
    }

    // ---------------------------------------------------------------- drawing (same order of values everywhere)

    static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        g.blit(texture, x, y, u, v, w, h, texW, texH);
    }

    static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, float u, float v, int w, int h,
                     int regionW, int regionH, int texW, int texH) {
        g.blit(texture, x, y, w, h, u, v, regionW, regionH, texW, texH);
    }

    static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        g.blitSprite(sprite, x, y, w, h);
    }

    /** 1.21.1 draws a tooltip when the screen is done, if the screen was given one this frame. */
    static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) return;
        List<FormattedCharSequence> text = new java.util.ArrayList<>();
        for (Component line : lines) text.addAll(font.split(line, Integer.MAX_VALUE));
        screen.setTooltipForNextRenderPass(text);
    }

    static void tooltip(GuiGraphics g, Font font, Component text, int mouseX, int mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.setTooltipForNextRenderPass(text);
    }

    /** What is drawn next goes above what was drawn so far (a dropdown over the buttons). */
    static void nextLayer(GuiGraphics g) {
        g.flush();
        g.pose().translate(0.0F, 0.0F, 300.0F);
    }

    // ---------------------------------------------------------------- links and buttons

    static Style withLink(Style style, URI uri) {
        return style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, uri.toString()));
    }

    static URI linkOf(Style style) {
        ClickEvent e = style.getClickEvent();
        if (e == null || e.getAction() != ClickEvent.Action.OPEN_URL) return null;
        try {
            return URI.create(e.getValue());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** The style under the mouse in a line of text drawn at x, y, or null. */
    static Style styleAt(Font font, int x, int y, FormattedCharSequence text, int mouseX, int mouseY) {
        if (mouseY < y || mouseY >= y + font.lineHeight || mouseX < x) return null;
        return font.getSplitter().componentStyleAtWidth(text, mouseX - x);
    }

    /** A 20x20 button with a 15x15 icon and the text as its tooltip. */
    static AbstractWidget iconButton(Component text, Button.OnPress press, ResourceLocation icon) {
        SpriteIconButton b = SpriteIconButton.builder(text, press, true).size(20, 20).sprite(icon, 15, 15).build();
        b.setTooltip(Tooltip.create(text));
        return b;
    }
}
