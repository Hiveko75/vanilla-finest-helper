package io.github.hiveko.vanillafinest.helper.setup;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.List;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The parts of the setup screen that differ by Minecraft version: 26.3 (SDL3 windowing, OpenGL or Vulkan). */
final class Compat {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");

    private Compat() {}

    /** A keyboard key as malilib keeps it: 26.3 keeps scan codes, like Minecraft's own key values. */
    static InputConstants.Key keyboard(int code) {
        return InputConstants.Type.KEYBOARD.getOrCreate(code);
    }

    /** The last picker's callback; freed when the next picker opens (never while SDL may still call it). */
    private static SDL_DialogFileCallback lastCallback;
    private static boolean pickerOpen;

    /**
     * Opens the system's folder picker (SDL3, as Minecraft uses); {@code done} gets the folder on the main
     * thread, or is not called when the player cancels.
     */
    static void choose(Path start, Consumer<Path> done) {
        Minecraft mc = Minecraft.getInstance();
        if (pickerOpen) return; // one picker at a time
        if (lastCallback != null) {
            lastCallback.free();
            lastCallback = null;
        }
        SDL_DialogFileCallback callback = SDL_DialogFileCallback.create((userdata, filelist, filter) -> {
            Path chosen = null;
            try {
                if (filelist != 0) {
                    long first = MemoryUtil.memGetAddress(filelist);
                    if (first != 0) chosen = Paths.get(MemoryUtil.memUTF8(first));
                }
            } catch (RuntimeException e) {
                chosen = null;
            }
            Path result = chosen;
            mc.execute(() -> {
                pickerOpen = false;
                if (result != null) done.accept(result);
            });
        });
        lastCallback = callback;
        pickerOpen = true;
        try {
            String location = start != null && Files.isDirectory(start) ? start.toString() : null;
            SDLDialog.SDL_ShowOpenFolderDialog(callback, 0L, mc.getWindow().handle(), location, false);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Setup screen: cannot open the folder picker ({})", e.toString());
            pickerOpen = false;
        }
    }

    /** Shows a screen (26.2 and newer: through {@code Minecraft.gui}). */
    static void setScreen(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /** The screen shown now. */
    static Screen screen() {
        return Minecraft.getInstance().gui.screen();
    }

    /** Minecraft's own Graphics API button (OpenGL / Vulkan, used after a restart). */
    static AbstractWidget graphicsApiButton(Options options, int x, int y, int width) {
        return options.preferredGraphicsBackend().createButton(options, x, y, width);
    }

    /** The chosen graphics API's name, for the summary. */
    static String graphicsApiName(Options options) {
        return options.preferredGraphicsBackend().get().caption().getString();
    }

    /** True when the game runs on Vulkan now (not the setting, which counts after a restart). */
    static boolean runningOnVulkan() {
        try {
            String name = RenderSystem.getDevice().getDeviceInfo().backendName();
            return name != null && name.toLowerCase(Locale.ROOT).contains("vulkan");
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    /** Where the render distance slider's knob sits for this many chunks. */
    static double renderDistancePosition(Options options, int chunks) {
        if (options.renderDistance().values() instanceof OptionInstance.IntRangeBase range) return range.toSliderValue(chunks);
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
