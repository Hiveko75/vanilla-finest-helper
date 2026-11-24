package io.github.hiveko.vanillafinest.helper.config;

import java.nio.file.Path;

import io.github.hiveko.vanillafinest.helper.folders.Folders;
import io.github.hiveko.vanillafinest.helper.folders.Folders.Folder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The helper's settings screen (Cloth Config, opened from Mod Menu). For each folder of the Folders
 * module: where it is (the modpack folder, i.e. the mod's own folder in this instance, which is the
 * default; Minecraft, the launcher's shared .minecraft folder; or another folder) and, for "another folder", the path. Saving
 * writes config/vanilla-finest-helper.properties.
 */
public final class HelperConfigScreen {
    private HelperConfigScreen() {}

    public enum Place {
        INSTANCE("Modpack folder"),
        SHARED("Minecraft"),
        OTHER("Another folder");

        final String label;

        Place(String label) {
            this.label = label;
        }
    }

    /** True when the screen has something to show (a folder the helper redirects in this game). */
    public static boolean hasOptions() {
        for (Folder folder : Folder.values()) {
            if (folder.active()) return true;
        }
        return false;
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Vanilla Finest Helper"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.literal("Folders"));

        category.addEntry(entries.startTextDescription(Component.literal(
                "Folders for mods that have no setting of their own. Modpack folder (the default) keeps the files in "
                        + "this instance; Minecraft is the launcher's .minecraft folder, so every instance uses the same "
                        + "files. Files are never moved."))
                .build());

        Runnable[] saves = new Runnable[Folder.values().length];
        int i = 0;
        for (Folder folder : Folder.values()) {
            if (!folder.active()) continue;
            Path shared = folder.shared();
            String value = Folders.value(folder);
            Place[] place = {placeOf(value, shared)};
            String[] other = {place[0] == Place.OTHER ? value : ""};

            category.addEntry(entries.startEnumSelector(Component.literal(folder.title), Place.class, place[0])
                    .setDefaultValue(Place.INSTANCE)
                    .setEnumNameProvider(p -> Component.literal(((Place) p).label))
                    .setTooltip(tooltip(folder, shared))
                    .setSaveConsumer(p -> place[0] = p)
                    .build());
            category.addEntry(entries.startStrField(Component.literal("  Another folder"), other[0])
                    .setDefaultValue("")
                    .setTooltip(Component.literal("Used when \"Another folder\" is chosen above. A full path, "
                            + "for example C:\\Minecraft\\" + folder.sharedSubfolder.replace('/', '\\') + "."))
                    .setSaveConsumer(text -> other[0] = text.trim())
                    .build());

            saves[i++] = () -> {
                switch (place[0]) {
                    case SHARED -> Folders.set(folder, shared != null ? shared.toString() : "");
                    case INSTANCE -> Folders.set(folder, "");
                    case OTHER -> Folders.set(folder, other[0]);
                }
            };
        }

        builder.setSavingRunnable(() -> {
            for (Runnable save : saves) {
                if (save != null) save.run();
            }
        });
        return builder.build();
    }

    private static Place placeOf(String value, Path shared) {
        if (value.isEmpty()) return Place.INSTANCE;
        if (shared != null && shared.toString().equals(value)) return Place.SHARED;
        return Place.OTHER;
    }

    private static Component[] tooltip(Folder folder, Path shared) {
        String own = folder == Folders.FLASHBACK
                ? "flashback\\replays in this instance"
                : "config\\axiom\\blueprints in this instance";
        String when = folder == Folders.FLASHBACK ? "A change counts at once." : "A change counts after a restart.";
        return new Component[] {
                Component.literal("Modpack folder: " + own),
                Component.literal("Minecraft: " + (shared != null ? shared : "not found on this computer")),
                Component.literal(when)
        };
    }

    /** True when Cloth Config is installed (the screen can be shown). */
    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("cloth-config");
    }
}
