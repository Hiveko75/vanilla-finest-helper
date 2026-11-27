package io.github.hiveko.vanillafinest.helper.setup;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.hiveko.vanillafinest.helper.HelperSettings;
import io.github.hiveko.vanillafinest.helper.guiscale.GuiScale;
import io.github.hiveko.vanillafinest.helper.setup.FolderSetup.Preset;
import io.github.hiveko.vanillafinest.helper.setup.FolderSetup.Row;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Vanilla Finest setup screen: one screen for everything the player sets, in five steps (Welcome,
 * Settings, Folders, Look, Ready to play). Opens on the first start, from the settings icon on the title
 * and pause screens, and from Mod Menu's Configure button.
 *
 * <p>Drawn like Options opened from the title screen: panorama, blur, the real Minecraft logo, then a
 * fixed panel (430×262 GUI pixels, centred under the logo) built from Minecraft's own widgets, font and
 * tooltips. When the window is too small the panel shrinks and the step's content scrolls.
 *
 * <p>Minecraft's own controls (languages, mouse, key bindings, graphics API, render distance, GUI scale)
 * apply at once, as in Options. Voxy, the folders and the look are applied with ✔ on the last step.
 */
public class SetupScreen extends Screen {
    private static final int PANEL_W = 430, PANEL_H = 262, HEADER = 16, FOOTER = 27, PAD_X = 16, PAD_Y = 7;
    private static final int LOGO_BOTTOM = LogoRenderer.DEFAULT_HEIGHT_OFFSET + LogoRenderer.LOGO_HEIGHT - 7 + 14;
    private static final int STEPS = 5;
    private static final int YELLOW = 0xFFFFFF55, WHITE = 0xFFFFFFFF, HINT = 0xFFC8C8C8, GREY = 0xFFA0A0A0;
    private static final String[] STEP_IDS = {"welcome", "settings", "folders", "look", "ready"};

    private final Screen parent;
    private final LogoRenderer logo = new LogoRenderer(false);
    private int step;

    // choices applied with ✔
    private boolean loaded;
    private Boolean voxyOn;
    private boolean voxyInitial;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Row, String> folders = new EnumMap<>(Row.class);
    private final Map<Row, String> foldersInitial = new EnumMap<>(Row.class);
    private List<Look.Profile> profiles = List.of();
    private Look.Profile profile, profileInitial;
    private List<Look.Shader> shaders = List.of();
    private Look.Shader shader, shaderInitial;
    private Markdown welcome;

    // layout of the current step
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int contentNeeded, scroll;
    private final List<AbstractWidget> content = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> paints = new ArrayList<>();
    private final List<HoverTip> hoverTips = new ArrayList<>();
    private Dropdown dropdown;
    private List<String> shownLanguages;

    private record HoverTip(int x, int y, int w, int h, List<Component> lines) {
        boolean over(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public SetupScreen(Screen parent) {
        super(Component.literal("Vanilla Finest"));
        this.parent = parent;
    }

    /** Opens the setup screen over the given screen (which it returns to). */
    public static void open(Screen parent) {
        Compat.setScreen(new SetupScreen(parent));
    }

    // ---------------------------------------------------------------- state

    private void loadState() {
        loaded = true;
        if (Integrations.voxyInstalled()) {
            voxyInitial = Integrations.voxyEnabled();
            voxyOn = voxyInitial;
        }
        for (Row row : Row.values()) {
            if (!FolderSetup.available(row)) continue;
            rows.add(row);
            String v = FolderSetup.read(row);
            folders.put(row, v);
            foldersInitial.put(row, v);
        }
        profiles = Look.profiles();
        profile = profileInitial = Look.current(profiles);
        shaders = Look.shaders();
        shaderInitial = Look.currentShader(shaders);
        shader = shaderInitial != null ? shaderInitial : Look.Shader.NONE;
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        SetupTexts.reload();
        if (!loaded) loadState();
        content.clear();
        paints.clear();
        hoverTips.clear();
        dropdown = null;

        pw = Math.min(PANEL_W, width - 12);
        int space = height - LOGO_BOTTOM;
        ph = Math.max(110, Math.min(PANEL_H, space - 9));
        px = (width - pw) / 2;
        py = LOGO_BOTTOM + Math.max(5, (space - ph - 7) / 2);
        if (py + ph > height) py = Math.max(0, height - ph);
        cx = px + PAD_X;
        cw = pw - 2 * PAD_X;
        cy = py + HEADER + PAD_Y;
        ch = py + ph - FOOTER - PAD_Y - cy;

        // footer: ◀, ▶ / ✔ (the Vanilla Finest logo is drawn between them)
        int fy = py + ph - FOOTER + 4;
        Button back = addRenderableWidget(Button.builder(Component.literal("◀"), b -> go(step - 1))
                .bounds(px + 12, fy, 20, 20).tooltip(Tooltip.create(text("button.back"))).build());
        back.active = step > 0;
        boolean last = step == STEPS - 1;
        addRenderableWidget(Button.builder(Component.literal(last ? "✔" : "▶"), b -> {
            if (last) applyAndClose();
            else go(step + 1);
        }).bounds(px + pw - 12 - 20, fy, 20, 20).tooltip(Tooltip.create(text(last ? "button.done" : "button.next"))).build());

        contentNeeded = 0;
        switch (step) {
            case 0 -> buildWelcome();
            case 1 -> buildSettings();
            case 2 -> buildFolders();
            case 3 -> buildLook();
            default -> buildReady();
        }
        int maxScroll = Math.max(0, contentNeeded - ch);
        if (scroll > maxScroll) {
            scroll = maxScroll;
            rebuildWidgets();
            return;
        }
        for (AbstractWidget w : content) {
            w.visible = w.getY() >= cy - 1 && w.getBottom() <= cy + ch + 1;
        }
    }

    private void go(int newStep) {
        step = Math.max(0, Math.min(STEPS - 1, newStep));
        scroll = 0;
        rebuildWidgets();
    }

    /** Top of the step's content (moves when the content scrolls). */
    private int top() {
        return cy - scroll;
    }

    private <T extends AbstractWidget> T add(T widget) {
        content.add(addRenderableWidget(widget));
        return widget;
    }

    private Button button(Component label, int x, int y, int w, Button.OnPress onPress) {
        return add(Button.builder(label, onPress).bounds(x, y, w, 20).build());
    }

    private static Component text(String key) {
        return Component.literal(SetupTexts.format(key));
    }

    private List<FormattedCharSequence> wrap(String text, int width) {
        return font.split(Component.literal(text), Math.max(20, width));
    }

    private static int linesHeight(List<FormattedCharSequence> lines) {
        return lines.isEmpty() ? 0 : lines.size() * 10 - 1;
    }

    private void paintLines(List<FormattedCharSequence> lines, int x, int y, int color) {
        paints.add(g -> {
            int ly = y;
            for (FormattedCharSequence l : lines) {
                g.text(font, l, x, ly, color, true);
                ly += 10;
            }
        });
    }

    private void paintBox(int x, int y, int w, int h) {
        paints.add(g -> {
            g.fill(x, y, x + w, y + h, 0x40000000);
            g.outline(x, y, w, h, 0x26FFFFFF);
        });
    }

    /** Positions for stacked elements: extra height is shared out between them (none before or after). */
    private static int[] spread(int top, int available, int[] heights, int minGap) {
        int sum = 0;
        for (int h : heights) sum += h;
        int n = heights.length;
        int gap = n > 1 ? Math.max(minGap, (available - sum) / (n - 1)) : 0;
        int[] ys = new int[n];
        int y = top;
        for (int i = 0; i < n; i++) {
            ys[i] = y;
            y += heights[i] + gap;
        }
        return ys;
    }

    private static int needed(int[] heights, int minGap) {
        int sum = 0;
        for (int h : heights) sum += h;
        return sum + Math.max(0, heights.length - 1) * minGap;
    }

    // ---------------------------------------------------------------- 1. Welcome

    private void buildWelcome() {
        List<String[]> links = new ArrayList<>();
        for (JsonElement e : SetupTexts.array("welcome.links")) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            if (o.has("label") && o.has("url")) links.add(new String[]{o.get("label").getAsString(), o.get("url").getAsString()});
        }
        int linksH = links.isEmpty() ? 0 : 26;
        welcome = new Markdown(font, SetupTexts.welcome());
        welcome.setArea(cx, cy, cw, Math.max(30, ch - linksH));
        contentNeeded = ch;
        if (!links.isEmpty()) {
            int n = Math.min(4, links.size());
            int w = (cw - (n - 1) * 6) / n;
            for (int i = 0; i < n; i++) {
                String[] l = links.get(i);
                URI uri;
                try {
                    uri = URI.create(l[1]);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                URI target = uri;
                button(Component.literal(l[0]), cx + i * (w + 6), cy + ch - 20, w,
                        b -> ConfirmLinkScreen.confirmLinkNow(this, target));
            }
        }
    }

    // ---------------------------------------------------------------- 2. Settings

    private void buildSettings() {
        int innerX = cx + 7, innerW = cw - 14;
        int colW = (innerW - 8) / 2;

        // Language & Controls
        List<String> langs = Integrations.languages();
        shownLanguages = langs;
        String hint1 = languagesText(langs);
        String added = HelperSettings.get("setup.language.added", "");
        if (!added.isEmpty() && langs.contains(added)) {
            hint1 += " " + SetupTexts.format("settings.languages.added", Integrations.languageName(added, false));
        }
        List<FormattedCharSequence> hintLines1 = wrap(hint1, innerW);

        String hint2 = SetupTexts.format("settings.graphics.hint");
        List<FormattedCharSequence> hintLines2 = wrap(hint2, innerW);

        int[] a = {9, 20, linesHeight(hintLines1), 20};
        int[] b = {9, 20, 20, linesHeight(hintLines2)};
        int needA = needed(a, 3) + 10, needB = needed(b, 3) + 10;
        int boxH = Math.max(Math.max(needA, needB), (ch - 6) / 2);
        contentNeeded = boxH * 2 + 6;

        // box A
        int ya = top();
        paintBox(cx, ya, cw, boxH);
        int[] pa = spread(ya + 5, boxH - 10, a, 3);
        paintLines(List.of(text("settings.section.controls").getVisualOrderText()), innerX, pa[0], YELLOW);
        Button languages = button(text("settings.languages"), innerX, pa[1], colW,
                btn -> Compat.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())));
        languages.setTooltip(Tooltip.create(text("settings.languages.tooltip")));
        add(new SensitivitySlider(innerX + colW + 8, pa[1], colW));
        paintLines(hintLines1, innerX, pa[2], HINT);
        String keyProfile = Integrations.keyProfile();
        Component keysLabel = keyProfile != null
                ? Component.literal(SetupTexts.format("settings.keybinds", keyProfile))
                : text("settings.keybinds.plain");
        boolean keycaps = Integrations.keycaps();
        button(keysLabel, innerX, pa[3], keycaps ? colW : innerW,
                btn -> Compat.setScreen(new KeyBindsScreen(this, minecraft.options)));
        if (keycaps) {
            button(text("settings.keymap"), innerX + colW + 8, pa[3], colW, btn -> Integrations.openKeycaps(this))
                    .setTooltip(Tooltip.create(text("settings.keymap.tooltip")));
        }

        // box B: Graphics
        int yb = top() + boxH + 6;
        paintBox(cx, yb, cw, boxH);
        int[] pb = spread(yb + 5, boxH - 10, b, 3);
        paintLines(List.of(text("settings.section.graphics").getVisualOrderText()), innerX, pb[0], YELLOW);
        add(Compat.graphicsApiButton(minecraft.options, innerX, pb[1], colW));
        boolean vulkan = Integrations.runningOnVulkan();
        if (voxyOn != null) {
            Button voxy = button(Component.literal(SetupTexts.format("settings.voxy", SetupTexts.get(voxyOn ? "on" : "off"))),
                    innerX + colW + 8, pb[1], colW, btn -> {
                        voxyOn = !voxyOn;
                        rebuildWidgets();
                    });
            voxy.active = !vulkan;
            voxy.setTooltip(Tooltip.create(text(vulkan ? "settings.voxy.vulkan" : "settings.voxy.tooltip")));
        } else {
            Button voxy = button(text("settings.voxy.missing"), innerX + colW + 8, pb[1], colW, btn -> {});
            voxy.active = false;
        }
        add(new GuiScaleSlider(innerX, pb[2], colW));
        if (voxyOn != null && voxyOn && !vulkan) {
            LockedRenderDistance rd = add(new LockedRenderDistance(innerX + colW + 8, pb[2], colW));
            rd.setTooltip(Tooltip.create(text("settings.renderdistance.locked")));
        } else {
            add(minecraft.options.renderDistance().createButton(minecraft.options, innerX + colW + 8, pb[2], colW));
        }
        paintLines(hintLines2, innerX, pb[3], HINT);
    }

    /**
     * The chosen languages under the Languages button: the menu language, and (with Language Reload) the
     * others that inventory search also uses, with "stone" in each of them as the example, read from that
     * language's own file ("Stein" for German).
     */
    private String languagesText(List<String> langs) {
        String main = Integrations.languageName(langs.get(0), true);
        if (!Integrations.loaded("languagereload")) return SetupTexts.format("settings.languages.single", main);
        if (langs.size() == 1) return SetupTexts.format("settings.languages.one", main);
        List<String> extra = new ArrayList<>();
        for (int i = 1; i < langs.size(); i++) extra.add(Integrations.languageName(langs.get(i), false));
        List<String> words = new ArrayList<>();
        for (String code : langs) {
            String w = Integrations.translationIn(code, "block.minecraft.stone");
            if (w != null && !w.isBlank()) {
                String quoted = SetupTexts.format("settings.languages.word", w);
                if (!words.contains(quoted)) words.add(quoted);
            }
        }
        return SetupTexts.format("settings.languages.more", main, String.join(", ", extra), String.join(", ", words));
    }

    /** Back from the language screen: the languages may change a moment later, so the step is redrawn then. */
    @Override
    public void tick() {
        super.tick();
        if (step == 1 && shownLanguages != null && !shownLanguages.equals(Integrations.languages())) rebuildWidgets();
    }

    /**
     * The render distance slider while Voxy is on: Minecraft's slider, greyed out at 16 chunks (Voxy draws the
     * land beyond it), with Minecraft's own label.
     */
    private final class LockedRenderDistance extends AbstractSliderButton {
        static final int CHUNKS = 16;

        LockedRenderDistance(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), position());
            active = false;
            updateMessage();
        }

        private static double position() {
            return Compat.renderDistancePosition(net.minecraft.client.Minecraft.getInstance().options, CHUNKS);
        }

        @Override
        protected void updateMessage() {
            setMessage(net.minecraft.client.Options.genericValueLabel(Component.translatable("options.renderDistance"),
                    Component.translatable("options.chunks", CHUNKS)));
        }

        @Override
        protected void applyValue() {
            // fixed while Voxy is on
        }
    }

    /**
     * Minecraft's mouse sensitivity, labelled "Mouse Sensitivity" (Minecraft's own slider just says
     * "Sensitivity"). Same values and steps as Options: 0–200%, with Minecraft's names for the two ends.
     */
    private final class SensitivitySlider extends AbstractSliderButton {
        SensitivitySlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), minecraft.options.sensitivity().get());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            Component v;
            if (value <= 0.0) v = Component.translatable("options.sensitivity.min");
            else if (value >= 1.0) v = Component.translatable("options.sensitivity.max");
            else v = Component.literal((int) (value * 200.0) + "%");
            setMessage(Component.literal(SetupTexts.format("settings.mouse", v.getString())));
        }

        @Override
        protected void applyValue() {
            minecraft.options.sensitivity().set(value);
        }
    }

    /** GUI scale: far left Automatic (N), then 1 … the biggest scale that fits; applied on release. */
    private final class GuiScaleSlider extends AbstractSliderButton {
        private final int max;
        private boolean dragging;
        private int applied;

        GuiScaleSlider(int x, int y, int w) {
            super(x, y, w, 20, Component.empty(), 0);
            max = GuiScale.maxScale();
            int current = GuiScale.automatic() ? 0 : Math.min(max, minecraft.options.guiScale().get());
            applied = current;
            value = max <= 0 ? 0 : current / (double) max;
            updateMessage();
            setTooltip(Tooltip.create(text("settings.guiscale.tooltip")));
        }

        private int position() {
            return (int) Math.round(value * max);
        }

        @Override
        protected void updateMessage() {
            int p = position();
            String v = p == 0 ? SetupTexts.format("settings.guiscale.auto", GuiScale.automaticScaleNow()) : String.valueOf(p);
            setMessage(Component.literal(SetupTexts.format("settings.guiscale", v)));
        }

        @Override
        protected void applyValue() {
            if (!dragging) commit();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            dragging = true;
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            dragging = false;
            commit();
        }

        private void commit() {
            int p = position();
            if (p == applied) return;
            applied = p;
            GuiScale.choose(p); // resizes the GUI, which builds this screen again
        }
    }

    // ---------------------------------------------------------------- 3. Folders

    private void buildFolders() {
        Preset preset = FolderSetup.matching(folders, rows);
        boolean oneDrive = false;
        for (Row row : rows) oneDrive |= FolderSetup.inOneDrive(FolderSetup.effective(row, folders.get(row)));

        int innerW = cw - 14;
        List<List<FormattedCharSequence>> hints = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String key = "folders.hint." + i;
            if (SetupTexts.has(key)) hints.add(wrap(SetupTexts.format(key), innerW));
        }
        if (oneDrive) hints.add(wrap(SetupTexts.format("folders.onedrive"), innerW));
        int boxContent = 9;
        for (List<FormattedCharSequence> h : hints) boxContent += 2 + linesHeight(h);
        int boxNeed = boxContent + 8;
        int rowsH = rows.isEmpty() ? 9 : rows.size() * 20 + (rows.size() - 1);

        int[] parts = {20, 9, boxNeed, rowsH};
        int need = needed(parts, 5);
        int boxH = boxNeed + Math.max(0, ch - need);
        contentNeeded = need + (boxH - boxNeed);
        int y = top();

        // presets
        Preset[] presets = Preset.values();
        int bw = (cw - 3 * 6) / 4;
        for (int i = 0; i < presets.length; i++) {
            Preset p = presets[i];
            MutableComponent label = Component.literal(SetupTexts.format("folders.preset." + p.name().toLowerCase(java.util.Locale.ROOT)));
            if (p == preset) label = label.withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
            Button b = button(label, cx + i * (bw + 6), y, bw, btn -> {
                for (Row row : rows) folders.put(row, FolderSetup.target(p, row));
                rebuildWidgets();
            });
            b.active = p == Preset.MODPACK || FolderSetup.base(p) != null;
            b.setTooltip(Tooltip.create(text("folders.preset." + p.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip")));
        }
        y += 20 + 5;

        // the base folder of the chosen preset
        String where;
        if (preset == Preset.MODPACK) where = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().toString();
        else if (preset != null) where = Objects.toString(FolderSetup.base(preset), "");
        else where = SetupTexts.format("folders.mixed");
        String whereText = cutLeft(where, cw);
        int whereY = y;
        paints.add(g -> g.text(font, whereText, cx, whereY, WHITE, true));
        y += 9 + 5;

        // Good to know
        paintBox(cx, y, cw, boxH);
        int[] heights = new int[hints.size() + 1];
        heights[0] = 9;
        for (int i = 0; i < hints.size(); i++) heights[i + 1] = linesHeight(hints.get(i));
        int innerTop = y + 4 + Math.max(0, (boxH - boxNeed) / 2);
        int[] hy = spread(innerTop, boxContent, heights, 2);
        paintLines(List.of(text("folders.box.title").getVisualOrderText()), cx + 7, hy[0], YELLOW);
        for (int i = 0; i < hints.size(); i++) {
            boolean od = oneDrive && i == hints.size() - 1;
            paintLines(hints.get(i), cx + 7, hy[i + 1], od ? YELLOW : HINT);
        }
        y += boxH + 5;

        // one row per folder
        if (rows.isEmpty()) {
            int ny = y;
            paints.add(g -> g.text(font, SetupTexts.format("folders.none"), cx, ny, GREY, true));
            return;
        }
        int nameW = Math.min(120, cw / 3);
        int changeW = Math.max(50, font.width(SetupTexts.format("folders.change")) + 12);
        for (Row row : rows) {
            int ry = y;
            String name = SetupTexts.format("folders.row." + row.textKey);
            Path path = FolderSetup.effective(row, folders.get(row));
            String pathText = cutLeft(path.toString(), cw - nameW - changeW - 8);
            boolean changed = !Objects.equals(folders.get(row), foldersInitial.get(row));
            paints.add(g -> {
                g.text(font, font.plainSubstrByWidth(name, nameW - 4), cx, ry + 6, changed ? YELLOW : WHITE, true);
                g.text(font, pathText, cx + nameW, ry + 6, HINT, true);
            });
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(name).withStyle(ChatFormatting.WHITE));
            tip.add(Component.literal(path.toString()).withStyle(ChatFormatting.GRAY));
            if (FolderSetup.needsRestart(row)) tip.add(text("folders.restart").copy().withStyle(ChatFormatting.YELLOW));
            hoverTips.add(new HoverTip(cx, ry, cw - changeW - 4, 20, tip));
            button(text("folders.change"), cx + cw - changeW, ry, changeW, btn ->
                    FolderSetup.choose(path, chosen -> {
                        String v = FolderSetup.same(chosen, FolderSetup.effective(row, "")) ? "" : chosen.toString();
                        folders.put(row, v);
                        if (Compat.screen() == this) rebuildWidgets();
                    }));
            y += 21;
        }
    }

    /** Cuts a path from the left ("…\Vanilla Finest 26.3\screenshots") to fit the width. */
    private String cutLeft(String s, int width) {
        if (font.width(s) <= width) return s;
        String dots = "…";
        int start = 0;
        while (start < s.length() && font.width(dots + s.substring(start)) > width) start++;
        return dots + s.substring(start);
    }

    // ---------------------------------------------------------------- 4. Look

    private void buildLook() {
        contentNeeded = ch;
        int colW = (cw - 6) / 2;
        if (profile == null) {
            List<FormattedCharSequence> lines = wrap(SetupTexts.format("look.noprofiles"), cw);
            paintLines(lines, cx, cy, HINT);
            return;
        }
        boolean vulkan = Integrations.runningOnVulkan();
        boolean iris = Integrations.Iris.installed();
        Button pb = button(Component.literal(SetupTexts.format("look.profile", profile.name()) + "  ▾"), cx, cy, colW,
                btn -> openProfiles(cx, cy + 21, colW));
        pb.setTooltip(Tooltip.create(text("look.profile.tooltip")));
        int sx = cx + colW + 6;
        Button sb = button(Component.literal(SetupTexts.format("look.shaders", shaderName(shader) + "§r") + "  ▾"),
                sx, cy, colW, btn -> openShaders(sx + colW, cy + 21, colW));
        sb.active = iris && !vulkan;
        if (!sb.active) sb.setTooltip(Tooltip.create(text(vulkan ? "look.shaders.vulkan" : "look.shaders.missing")));
        else if (!shaderTip(shader).isEmpty()) sb.setTooltip(Tooltip.create(Component.literal(shaderTip(shader))));

        // preview: 16:9, as big as fits under the buttons
        int areaTop = cy + 26, areaH = ch - 26;
        int prevH = Math.min(areaH, cw * 9 / 16);
        int prevW = prevH * 16 / 9;
        int prevX = cx + (cw - prevW) / 2, prevY = areaTop + (areaH - prevH) / 2;
        String title = SetupTexts.format("look.preview");
        String combo = profile.name() + " + " + plainShaderName(shader);
        String soon = SetupTexts.format("look.preview.soon");
        paints.add(g -> {
            g.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xFF000000);
            g.fill(prevX + 1, prevY + 1, prevX + prevW - 1, prevY + prevH - 1, 0xFF263626);
            g.outline(prevX - 1, prevY - 1, prevW + 2, prevH + 2, 0x59FFFFFF);
            int mid = prevY + prevH / 2;
            g.centeredText(font, title, prevX + prevW / 2, mid - 15, HINT);
            g.centeredText(font, combo, prevX + prevW / 2, mid - 4, WHITE);
            g.centeredText(font, soon, prevX + prevW / 2, mid + 9, GREY);
        });
        hoverTips.add(new HoverTip(prevX, prevY, prevW, prevH, profileTooltip(profile)));
    }

    private void openProfiles(int x, int y, int w) {
        List<Dropdown.Item> items = new ArrayList<>();
        for (Look.Profile p : profiles) {
            items.add(new Dropdown.Item(p.name(), profileTooltip(p), p == profile, () -> {
                profile = p;
                rebuildWidgets();
            }));
        }
        dropdown = new Dropdown(x, y, w, items);
    }

    /**
     * The shader list under its button, right-aligned to it and as wide as the longest name needs: the
     * order, colours and tooltips come from Iris Shader Folder (see {@link Look#shaders()}).
     */
    private void openShaders(int right, int y, int minW) {
        List<Dropdown.Item> items = new ArrayList<>();
        int w = minW;
        for (Look.Shader s : shaders) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(shaderName(s)).withStyle(ChatFormatting.WHITE));
            for (String line : shaderTip(s).split("\n")) {
                if (line.isBlank()) continue;
                for (FormattedCharSequence l : wrap(line.strip(), 220)) tip.add(toComponent(l).withStyle(ChatFormatting.GRAY));
            }
            items.add(new Dropdown.Item(shaderName(s), tip, s.equals(shader), () -> {
                shader = s;
                rebuildWidgets();
            }));
            w = Math.max(w, font.width(shaderName(s)) + Dropdown.PADDING);
        }
        w = Math.min(w, right - px - 4);
        dropdown = new Dropdown(right - w, y, w, items);
    }

    /** The shader's tooltip text: Iris Shader Folder's (the pack's own description and the pack's text). */
    private static String shaderTip(Look.Shader s) {
        if (s == null || s.none()) return SetupTexts.has("shader.none.desc") ? SetupTexts.format("shader.none.desc") : "";
        return s.tooltip();
    }

    private List<Component> profileTooltip(Look.Profile p) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(p.name()).withStyle(ChatFormatting.WHITE));
        for (String line : p.description().split("\n")) {
            if (line.isBlank()) continue;
            for (FormattedCharSequence l : wrap(line.strip(), 240)) tip.add(toComponent(l).withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    private static MutableComponent toComponent(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((pos, style, cp) -> {
            sb.appendCodePoint(cp);
            return true;
        });
        return Component.literal(sb.toString());
    }

    /** The shader's name as Iris's list shows it, with Iris Shader Folder's colours (§ codes). */
    private static String shaderName(Look.Shader s) {
        if (s == null || s.none()) return SetupTexts.format("shader.none");
        return s.name();
    }

    private static String plainShaderName(Look.Shader s) {
        String plain = ChatFormatting.stripFormatting(shaderName(s));
        return plain != null ? plain : "";
    }

    // ---------------------------------------------------------------- 5. Ready to play

    private void buildReady() {
        List<String[]> lines = new ArrayList<>();
        List<String> langs = Integrations.languages();
        String main = Integrations.languageName(langs.get(0), true);
        if (langs.size() > 1) {
            List<String> extra = new ArrayList<>();
            for (int i = 1; i < langs.size(); i++) extra.add(Integrations.languageName(langs.get(i), false));
            lines.add(new String[]{"ready.languages", SetupTexts.format("ready.languages.more", main, String.join(", ", extra))});
        } else {
            lines.add(new String[]{"ready.languages", main});
        }
        lines.add(new String[]{"ready.mouse", (int) (minecraft.options.sensitivity().get() * 200.0) + "%"});
        String keyProfile = Integrations.keyProfile();
        lines.add(new String[]{"ready.keybinds", keyProfile != null ? SetupTexts.format("ready.keybinds.profile", keyProfile)
                : SetupTexts.format("ready.keybinds.default")});
        String api = Compat.graphicsApiName(minecraft.options);
        String voxy = voxyOn == null ? SetupTexts.format("ready.voxy.missing")
                : SetupTexts.format(voxyOn ? "ready.voxy.on" : "ready.voxy.off");
        int rd = voxyOn != null && voxyOn ? 16 : minecraft.options.renderDistance().get();
        lines.add(new String[]{"ready.graphics", SetupTexts.format("ready.graphics.value", api, voxy, rd)});
        lines.add(new String[]{"ready.guiscale", GuiScale.automatic()
                ? SetupTexts.format("ready.guiscale.auto", GuiScale.automaticScaleNow())
                : String.valueOf(minecraft.options.guiScale().get())});
        if (!rows.isEmpty()) {
            Preset preset = FolderSetup.matching(folders, rows);
            lines.add(new String[]{"ready.folders", SetupTexts.format(preset == null ? "ready.folders.mixed"
                    : "ready.folders." + preset.name().toLowerCase(java.util.Locale.ROOT))});
        }
        if (profile != null) {
            lines.add(new String[]{"ready.look", SetupTexts.format("ready.look.value", profile.name(), plainShaderName(shader))});
        }

        int keyW = 0;
        for (String[] l : lines) keyW = Math.max(keyW, font.width(SetupTexts.format(l[0])));
        keyW = Math.min(keyW + 10, cw / 3);
        int valueW = cw - 28 - keyW;
        List<List<FormattedCharSequence>> values = new ArrayList<>();
        int rowsNeed = 0;
        for (String[] l : lines) {
            List<FormattedCharSequence> v = wrap(l[1], valueW);
            values.add(v);
            rowsNeed += linesHeight(v) + 3;
        }
        List<FormattedCharSequence> hint = wrap(SetupTexts.format("ready.hint"), cw);
        int hintH = linesHeight(hint);
        int boxNeed = 9 + 4 + rowsNeed + 10;
        int boxH = Math.max(boxNeed, ch - hintH - 6);
        contentNeeded = boxH + 6 + hintH;

        int y = top();
        paintBox(cx, y, cw, boxH);
        int[] heights = new int[lines.size() + 1];
        heights[0] = 9;
        for (int i = 0; i < lines.size(); i++) heights[i + 1] = linesHeight(values.get(i));
        int[] ys = spread(y + 5, boxH - 10, heights, 3);
        paintLines(List.of(text("ready.box.title").getVisualOrderText()), cx + 12, ys[0], YELLOW);
        int fKeyW = keyW;
        for (int i = 0; i < lines.size(); i++) {
            int ly = ys[i + 1];
            String key = SetupTexts.format(lines.get(i)[0]);
            List<FormattedCharSequence> v = values.get(i);
            paints.add(g -> g.text(font, key, cx + 12, ly, HINT, true));
            paintLines(v, cx + 12 + fKeyW, ly, WHITE);
        }
        paintLines(hint, cx, y + boxH + 6, HINT);
    }

    // ---------------------------------------------------------------- apply

    private void applyAndClose() {
        if (voxyOn != null && voxyOn != voxyInitial) Integrations.setVoxyEnabled(voxyOn);
        if (voxyOn != null && voxyOn && !Integrations.runningOnVulkan()) minecraft.options.renderDistance().set(16);
        for (Row row : rows) {
            if (!Objects.equals(folders.get(row), foldersInitial.get(row))) FolderSetup.write(row, folders.get(row));
        }
        SetupModule.updatePointers();
        boolean shaderChanged = shader != null && !shader.equals(shaderInitial)
                && Integrations.Iris.installed() && !Integrations.runningOnVulkan();
        boolean profileChanged = profile != null && !profile.equals(profileInitial);
        minecraft.options.save();
        HelperSettings.set(SetupModule.DONE_KEY, "true");
        Compat.setScreen(parent);
        if (shaderChanged) Integrations.Iris.apply(shader.file());
        if (profileChanged) Look.applyProfile(profile);
    }

    @Override
    public void onClose() {
        // Esc: the choices on this screen are dropped; Minecraft's own settings were applied already
        HelperSettings.set(SetupModule.DONE_KEY, "true");
        minecraft.options.save();
        Compat.setScreen(parent);
    }

    // ---------------------------------------------------------------- drawing

    private static final Identifier[] LOGOS = new Identifier[6];

    static {
        for (int i = 0; i < 6; i++) {
            LOGOS[i] = Identifier.fromNamespaceAndPath("vanilla-finest-helper", "textures/gui/setup/logo_" + (i + 1) + "x.png");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        logo.extractRenderState(g, width, 1.0F);

        // panel
        extractMenuBackground(g, px, py, pw, ph);
        boolean inWorld = minecraft.level != null;
        Identifier headerSep = inWorld ? INWORLD_HEADER_SEPARATOR : HEADER_SEPARATOR;
        Identifier footerSep = inWorld ? INWORLD_FOOTER_SEPARATOR : FOOTER_SEPARATOR;
        Compat.blit(g, footerSep, px, py, 0.0F, 0.0F, pw, 2, 32, 2);
        Compat.blit(g, headerSep, px, py + HEADER - 2, 0.0F, 0.0F, pw, 2, 32, 2);
        Compat.blit(g, footerSep, px, py + ph - FOOTER, 0.0F, 0.0F, pw, 2, 32, 2);
        Compat.blit(g, headerSep, px, py + ph - 2, 0.0F, 0.0F, pw, 2, 32, 2);

        // header: "Welcome · 1 / 5"
        MutableComponent header = Component.literal(SetupTexts.format("title." + STEP_IDS[step])).withStyle(ChatFormatting.WHITE)
                .append(Component.literal("  ·  " + SetupTexts.format("header.position", step + 1, STEPS)).withStyle(ChatFormatting.GRAY));
        g.centeredText(font, header, width / 2, py + 4, WHITE);

        // footer logo, pixel-exact at GUI scales 1-6
        int scale = Math.max(1, Math.min(6, (int) minecraft.getWindow().getGuiScale()));
        int lw = 129, lh = 15;
        int lx = px + (pw - lw) / 2, ly = py + ph - FOOTER + 2 + (FOOTER - 2 - lh) / 2;
        Compat.blit(g, LOGOS[scale - 1], lx, ly, 0.0F, 0.0F, lw, lh, lw * scale, lh * scale, lw * scale, lh * scale);

        // step content
        if (step == 0 && welcome != null) {
            welcome.extract(g, mouseX, mouseY);
        } else {
            g.enableScissor(px + 1, cy, px + pw - 1, cy + ch);
            for (Consumer<GuiGraphicsExtractor> p : paints) p.accept(g);
            g.disableScissor();
            if (contentNeeded > ch) {
                int barX = px + pw - 9, thumbH = Math.max(16, ch * ch / contentNeeded);
                int thumbY = cy + (int) ((ch - thumbH) * (scroll / (double) (contentNeeded - ch)));
                Compat.sprite(g, Identifier.withDefaultNamespace("widget/scroller_background"), barX, cy, 6, ch);
                Compat.sprite(g, Identifier.withDefaultNamespace("widget/scroller"), barX, thumbY, 6, thumbH);
            }
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        if (dropdown != null) {
            dropdown.extract(g, font, mouseX, mouseY);
        } else {
            for (HoverTip t : hoverTips) {
                if (t.over(mouseX, mouseY) && mouseY >= cy && mouseY < cy + ch) {
                    Compat.tooltip(g, font, t.lines(), mouseX, mouseY);
                    break;
                }
            }
            if (step == 0 && welcome != null) {
                URI link = welcome.linkAt(mouseX, mouseY);
                if (link != null) Compat.tooltip(g, font, Component.literal(link.toString()), mouseX, mouseY);
            }
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdown != null) {
            Dropdown d = dropdown;
            dropdown = null;
            if (d.click(event.x(), event.y())) return true;
            return true; // a click outside only closes the list
        }
        if (step == 0 && welcome != null) {
            if (welcome.clickedScrollbar(event.x(), event.y())) return true;
            URI link = welcome.linkAt(event.x(), event.y());
            if (link != null) {
                ConfirmLinkScreen.confirmLinkNow(this, link);
                return true;
            }
        }
        // content widgets scrolled out of the visible area are hidden, so they don't take clicks
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (step == 0 && welcome != null && welcome.dragged(event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (welcome != null) welcome.released();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (dropdown != null) return true;
        if (step == 0 && welcome != null && welcome.isOver(mx, my)) return welcome.scrolled(scrollY);
        if (contentNeeded > ch && mx >= px && mx < px + pw && my >= cy && my < cy + ch) {
            int next = (int) Math.max(0, Math.min(contentNeeded - ch, scroll - scrollY * 12));
            if (next != scroll) {
                scroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
