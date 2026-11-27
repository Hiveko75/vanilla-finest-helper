package io.github.hiveko.vanillafinest.helper.setup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Welcome page: a small Markdown subset drawn with the game's font, in a scrolling area.
 * <ul>
 *   <li>{@code #}, {@code ##} headings (yellow), {@code ###} subheadings (white);</li>
 *   <li>paragraphs, {@code - } list items, {@code **bold**}, {@code *italic*};</li>
 *   <li>{@code > } lines: a box like "Good to know" (a {@code ##} line inside is its yellow title);</li>
 *   <li>{@code [text](https://…)} links (through Minecraft's "open this link?" screen);</li>
 *   <li>{@code {key:<binding id>}}: the key's current name, in yellow; § colour codes pass through.</li>
 * </ul>
 */
final class Markdown {
    static final int YELLOW = 0xFFFFFF55, WHITE = 0xFFFFFFFF, TEXT = 0xFFE0E0E0;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND = Identifier.withDefaultNamespace("widget/scroller_background");
    private static final int SCROLLBAR = 6, LINE = 10;

    /** A parsed block. */
    private record Block(Kind kind, Component text, List<Block> children) {}

    private enum Kind { TITLE, SUBTITLE, PARAGRAPH, ITEM, BOX }

    private record Line(FormattedCharSequence text, int x, int y, int color) {}

    private record Rect(int x, int y, int w, int h) {}

    private final Font font;
    private final List<Block> blocks;
    private final List<Line> lines = new ArrayList<>();
    private final List<Rect> boxes = new ArrayList<>();
    private int x, y, w, h, contentHeight;
    private double scroll;
    private boolean dragging;

    Markdown(Font font, String source) {
        this.font = font;
        String[] src = source.replace("\r", "").split("\n");
        this.blocks = parse(src, 0, src.length);
    }

    // ---------------------------------------------------------------- parsing

    private List<Block> parse(String[] src, int from, int to) {
        List<Block> out = new ArrayList<>();
        StringBuilder para = new StringBuilder();
        int i = from;
        while (i < to) {
            String raw = src[i];
            String line = raw.strip();
            if (line.startsWith(">")) {
                flush(out, para);
                List<String> inner = new ArrayList<>();
                while (i < to && src[i].strip().startsWith(">")) {
                    String l = src[i].strip().substring(1);
                    inner.add(l.startsWith(" ") ? l.substring(1) : l);
                    i++;
                }
                String[] arr = inner.toArray(new String[0]);
                out.add(new Block(Kind.BOX, null, parse(arr, 0, arr.length)));
                continue;
            }
            if (line.isEmpty()) {
                flush(out, para);
            } else if (line.startsWith("###")) {
                flush(out, para);
                out.add(new Block(Kind.SUBTITLE, inline(line.substring(3).strip(), Style.EMPTY.withColor(WHITE & 0xFFFFFF).withBold(true)), null));
            } else if (line.startsWith("#")) {
                flush(out, para);
                out.add(new Block(Kind.TITLE, inline(line.replaceFirst("^#+", "").strip(), Style.EMPTY.withColor(YELLOW & 0xFFFFFF)), null));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                flush(out, para);
                StringBuilder item = new StringBuilder(line.substring(2).strip());
                // continuation lines (indented) belong to the item
                while (i + 1 < to && src[i + 1].startsWith("  ") && !src[i + 1].strip().isEmpty()
                        && !src[i + 1].strip().startsWith("- ") && !src[i + 1].strip().startsWith("* ")) {
                    item.append(' ').append(src[++i].strip());
                }
                out.add(new Block(Kind.ITEM, inline(item.toString(), Style.EMPTY.withColor(TEXT & 0xFFFFFF)), null));
            } else {
                if (!para.isEmpty()) para.append(' ');
                para.append(line);
            }
            i++;
        }
        flush(out, para);
        return out;
    }

    private void flush(List<Block> out, StringBuilder para) {
        if (para.isEmpty()) return;
        out.add(new Block(Kind.PARAGRAPH, inline(para.toString(), Style.EMPTY.withColor(TEXT & 0xFFFFFF)), null));
        para.setLength(0);
    }

    /** Inline formatting: **bold**, *italic*, [links](url) and {key:…}. */
    static MutableComponent inline(String text, Style base) {
        MutableComponent out = Component.empty();
        StringBuilder plain = new StringBuilder();
        boolean bold = false, italic = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (text.startsWith("**", i)) {
                append(out, plain, base, bold, italic);
                bold = !bold;
                i += 2;
                continue;
            }
            if (c == '*' && (i + 1 < text.length() && text.charAt(i + 1) != ' ' || italic)) {
                append(out, plain, base, bold, italic);
                italic = !italic;
                i++;
                continue;
            }
            if (c == '[') {
                int close = text.indexOf("](", i);
                int end = close < 0 ? -1 : text.indexOf(')', close + 2);
                if (close > i && end > close) {
                    append(out, plain, base, bold, italic);
                    String label = text.substring(i + 1, close);
                    String url = text.substring(close + 2, end).strip();
                    Style style = styled(base, bold, italic).withColor(ChatFormatting.AQUA).withUnderlined(true);
                    try {
                        URI uri = URI.create(url);
                        if ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) {
                            style = Compat.withLink(style, uri);
                        }
                    } catch (IllegalArgumentException e) {
                        // not a link: shown as text
                    }
                    out.append(Component.literal(KeyNames.fill(label)).setStyle(style));
                    i = end + 1;
                    continue;
                }
            }
            if (text.startsWith("{key:", i)) {
                int end = text.indexOf('}', i);
                if (end > i) {
                    append(out, plain, base, bold, italic);
                    String name = KeyNames.name(text.substring(i + 5, end).strip());
                    out.append(Component.literal(name).setStyle(styled(base, bold, italic).withColor(YELLOW & 0xFFFFFF)));
                    i = end + 1;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        append(out, plain, base, bold, italic);
        return out;
    }

    private static void append(MutableComponent out, StringBuilder plain, Style base, boolean bold, boolean italic) {
        if (plain.isEmpty()) return;
        Style style = styled(base, bold, italic);
        if (bold) style = style.withColor(WHITE & 0xFFFFFF);
        out.append(Component.literal(plain.toString()).setStyle(style));
        plain.setLength(0);
    }

    private static Style styled(Style base, boolean bold, boolean italic) {
        Style s = base;
        if (bold) s = s.withBold(true);
        if (italic) s = s.withItalic(true);
        return s;
    }

    // ---------------------------------------------------------------- layout

    /** Places the text in the area; keeps the scroll position where possible. */
    void setArea(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        layout(w);
        if (contentHeight > h) layout(w - SCROLLBAR - 4);
        scroll = clamp(scroll);
    }

    private void layout(int width) {
        lines.clear();
        boxes.clear();
        contentHeight = layoutBlocks(blocks, 0, 0, width);
    }

    private int layoutBlocks(List<Block> list, int left, int top, int width) {
        int cy = top;
        boolean first = true;
        for (Block b : list) {
            switch (b.kind()) {
                case TITLE -> {
                    if (!first) cy += 4;
                    cy = text(b.text(), left, cy, width, YELLOW) + 3;
                }
                case SUBTITLE -> {
                    if (!first) cy += 4;
                    cy = text(b.text(), left, cy, width, WHITE) + 2;
                }
                case PARAGRAPH -> cy = text(b.text(), left, cy, width, TEXT) + 5;
                case ITEM -> {
                    lines.add(new Line(Component.literal("•").getVisualOrderText(), left + 1, cy, TEXT));
                    cy = text(b.text(), left + 8, cy, width - 8, TEXT) + 1;
                }
                case BOX -> {
                    cy += first ? 0 : 1;
                    int boxTop = cy;
                    int boxIndex = boxes.size();
                    boxes.add(null);
                    int inner = layoutBlocks(b.children(), left + 7, cy + 5, width - 14);
                    int bottom = inner + 2;
                    boxes.set(boxIndex, new Rect(left, boxTop, width, bottom - boxTop));
                    cy = bottom + 6;
                }
            }
            first = false;
        }
        // drop the last block's bottom gap
        return list.isEmpty() ? cy : Math.max(top, cy - 4);
    }

    private int text(Component text, int left, int top, int width, int color) {
        int cy = top;
        for (FormattedCharSequence seq : font.split(text, Math.max(20, width))) {
            lines.add(new Line(seq, left, cy, color));
            cy += LINE;
        }
        return cy;
    }

    private double clamp(double s) {
        return Math.max(0, Math.min(s, Math.max(0, contentHeight - h)));
    }

    // ---------------------------------------------------------------- drawing and input

    void extract(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int top = y - (int) Math.round(scroll);
        g.enableScissor(x, y, x + w, y + h);
        for (Rect r : boxes) {
            g.fill(x + r.x(), top + r.y(), x + r.x() + r.w(), top + r.y() + r.h(), 0x40000000);
            g.outline(x + r.x(), top + r.y(), r.w(), r.h(), 0x26FFFFFF);
        }
        for (Line l : lines) {
            int ly = top + l.y();
            if (ly + LINE < y || ly > y + h) continue;
            g.text(font, l.text(), x + l.x(), ly, l.color(), true);
        }
        g.disableScissor();
        if (contentHeight > h) {
            int barX = x + w - SCROLLBAR;
            int thumbH = Math.max(16, h * h / contentHeight);
            int thumbY = y + (int) ((h - thumbH) * (scroll / (contentHeight - h)));
            Compat.sprite(g, SCROLLER_BACKGROUND, barX, y, SCROLLBAR, h);
            Compat.sprite(g, SCROLLER, barX, thumbY, SCROLLBAR, thumbH);
        }
    }

    boolean isOver(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The style under the mouse (for links), or null. */
    Style styleAt(double mx, double my) {
        if (!isOver(mx, my)) return null;
        int top = y - (int) Math.round(scroll);
        for (Line l : lines) {
            int ly = top + l.y();
            if (my >= ly && my < ly + LINE) {
                Style s = Compat.styleAt(font, x + l.x(), ly, l.text(), (int) mx, (int) my);
                if (s != null) return s;
            }
        }
        return null;
    }

    /** The link under the mouse, or null. */
    URI linkAt(double mx, double my) {
        Style s = styleAt(mx, my);
        return s == null ? null : Compat.linkOf(s);
    }

    boolean scrolled(double amount) {
        if (contentHeight <= h) return false;
        scroll = clamp(scroll - amount * 12);
        return true;
    }

    boolean clickedScrollbar(double mx, double my) {
        if (contentHeight <= h || mx < x + w - SCROLLBAR || !isOver(mx, my)) return false;
        dragging = true;
        dragTo(my);
        return true;
    }

    boolean dragged(double my) {
        if (!dragging) return false;
        dragTo(my);
        return true;
    }

    void released() {
        dragging = false;
    }

    private void dragTo(double my) {
        int thumbH = Math.max(16, h * h / contentHeight);
        double f = (my - y - thumbH / 2.0) / Math.max(1, h - thumbH);
        scroll = clamp(f * (contentHeight - h));
    }
}
