package io.github.hiveko.vanillafinest.helper.setup;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
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
 * The parts of the setup screen that differ by Minecraft version: GLFW windowing (26.1.2), where the
 * folder picker is LWJGL's tinyfd (bundled with Minecraft); OpenGL only; screens through Minecraft.setScreen.
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

    static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH);
    }

    static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v, int w, int h,
                     int regionW, int regionH, int texW, int texH) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, regionW, regionH, texW, texH);
    }

    static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    static void tooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int mouseX, int mouseY) {
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    static void tooltip(GuiGraphicsExtractor g, Font font, Component text, int mouseX, int mouseY) {
        g.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }

    /** What is drawn next goes above what was drawn so far (a dropdown over the buttons). */
    static void nextLayer(GuiGraphicsExtractor g) {
        g.nextStratum();
    }

    // ---------------------------------------------------------------- links and buttons

    /** A style that opens this address when clicked. */
    static Style withLink(Style style, URI uri) {
        return style.withClickEvent(new ClickEvent.OpenUrl(uri));
    }

    /** The address a style opens, or null. */
    static URI linkOf(Style style) {
        return style.getClickEvent() instanceof ClickEvent.OpenUrl open ? open.uri() : null;
    }

    /** The style under the mouse in a line of text drawn at x, y, or null. */
    static Style styleAt(Font font, int x, int y, FormattedCharSequence text, int mouseX, int mouseY) {
        ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(font, mouseX, mouseY);
        finder.accept(x, y, text);
        return finder.result();
    }

    /** A 20x20 button with a 15x15 icon and the text as its tooltip. */
    static AbstractWidget iconButton(Component text, Button.OnPress press, Identifier icon) {
        return SpriteIconButton.builder(text, press, true).size(20, 20).sprite(icon, 15, 15).withTootip().build();
    }
}
