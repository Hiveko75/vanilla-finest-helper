package io.github.hiveko.vanillafinest.helper;

import io.github.hiveko.vanillafinest.helper.folders.Folders;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vanilla Finest Helper: the players' side of the Vanilla Finest modpacks: folders here; the setup screen and
 * the GUI scale module (26.3 for now) are in platform/mc26.3 (setup/, guiscale/), with their own client
 * entrypoint. The fixes for the packs' mods are a separate mod, Vanilla Finest Fixes.
 *
 * <ul>
 *   <li><b>Home folders</b> (homefolders/, all versions, off by default): on an instance's first start,
 *       points Screenshot Gallery, Litematica, Flashback and Axiom at the shared Minecraft folder. Runs
 *       before launch ({@code preLaunch} entrypoint), not from here.</li>
 *   <li><b>Folders</b> (folders/, all versions): Flashback's replay folder (without Flashback Settings)
 *       and Axiom's blueprint folder, from the helper's settings (config/vanilla-finest-helper.properties;
 *       the setup screen will set them too).</li>
 * </ul>
 * A folder is only handled when its mod is installed in a version the module was made for
 * ({@link ModuleCheck}); the log lists the active folders at start-up.
 */
public class VanillaFinestHelper implements ClientModInitializer {
    public static final String MOD_ID = "vanilla-finest-helper";
    public static final Logger LOGGER = LoggerFactory.getLogger("Vanilla Finest Helper");

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        for (Folders.Folder folder : Folders.Folder.values()) {
            if (folder.module.equals("flashback") && loader.isModLoaded("flashback-settings")) continue;
            if (check(folder.title, folder.module)) {
                String value = Folders.value(folder);
                LOGGER.info("Folders module active: {} in {}", folder.title,
                        value.isEmpty() ? "the mod's own folder in this instance" : value);
            }
        }
    }

    /**
     * True when the module is in this jar and its mod is installed in a version it was made for. When the
     * mod is installed in another version, logs that the module is off. Nothing is logged without the mod.
     */
    private static boolean check(String module, String key) {
        if (!ModuleCheck.inThisVersion(key) || !FabricLoader.getInstance().isModLoaded(ModuleCheck.modId(key))) {
            return false;
        }
        if (ModuleCheck.matches(key)) {
            return true;
        }
        LOGGER.warn("{} module off: made for {}, found {}", module, ModuleCheck.expectedText(key),
                ModuleCheck.installedVersion(ModuleCheck.modId(key)));
        return false;
    }
}
