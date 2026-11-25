package io.github.hiveko.vanillafinest.helper.setup;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A drop-down list under a button, like Multi Key Bindings' profile menu: black box with a white border,
 * one row per choice, the chosen one in yellow (a coloured name keeps its colours and gets a yellow ✔).
 * Hovering a row shows its description as a normal tooltip.
 */
final class Dropdown {
    static final int ROW = 13;
    /** Room around a label: 6 px on the left, the ✔ and a gap on the right. */
    static final int PADDING = 24;

    record Item(String label, List<Component> tooltip, boolean selected, Runnable pick) {}

    private final int x, y, w;
    private final List<Item> items;

    Dropdown(int x, int y, int w, List<Item> items) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.items = items;
    }

    private int height() {
        return items.size() * ROW + 4;
    }

    void extract(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        g.nextStratum();
        int h = height();
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.outline(x, y, w, h, 0xFFFFFFFF);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int ry = y + 2 + i * ROW;
            boolean hover = mouseX >= x + 1 && mouseX < x + w - 1 && mouseY >= ry && mouseY < ry + ROW;
            if (hover) {
                g.fill(x + 2, ry, x + w - 2, ry + ROW, 0xFF3A3A3A);
                if (!item.tooltip().isEmpty()) g.setComponentTooltipForNextFrame(font, item.tooltip(), mouseX, mouseY);
            }
            boolean colored = item.label().indexOf('\u00a7') >= 0;
            int color = item.selected() ? 0xFFFFFF55 : hover ? 0xFFFFFFFF : 0xFFE0E0E0;
            g.text(font, font.plainSubstrByWidth(item.label(), w - PADDING), x + 6, ry + 3, color, true);
            if (item.selected() && colored) g.text(font, "\u2714", x + w - 14, ry + 3, 0xFFFFFF55, true);
        }
    }

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + height();
    }

    /** Picks the row under the mouse; true when the click was inside the list. */
    boolean click(double mx, double my) {
        if (!contains(mx, my)) return false;
        int i = (int) ((my - y - 2) / ROW);
        if (i >= 0 && i < items.size()) items.get(i).pick().run();
        return true;
    }
}
