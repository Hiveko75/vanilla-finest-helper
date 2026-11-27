package io.github.hiveko.vanillafinest.helper;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the Folders module's Flashback mixin only when Flashback is installed in a version the module
 * was made for ({@link ModuleCheck}) and Flashback Settings is not (it sets the replay folder itself), and
 * the Iris Shader Folder tooltip mixin only with Shader Folder in a version it was made for. Otherwise they
 * are skipped, so nothing fails at startup.
 */
public class MixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String key = moduleKey(mixinClassName);
        if ("flashback".equals(key) && FabricLoader.getInstance().isModLoaded("flashback-settings")) {
            return false; // Flashback Settings sets the replay folder itself
        }
        return key == null || ModuleCheck.matches(key);
    }

    /** The {@link ModuleCheck} key a mixin belongs to, or null for mixins that always apply. */
    static String moduleKey(String mixinClassName) {
        if (mixinClassName.endsWith(".mixin.folders.FlashbackMixin")) return "flashback";
        if (mixinClassName.endsWith(".mixin.shaderfolder.ShaderFolderTooltipMixin")) return "iris_shader_folder";
        if (mixinClassName.endsWith(".mixin.shaderfolder.ShaderFolderDescriptionMixin")) return "iris_shader_folder";
        if (mixinClassName.endsWith(".mixin.packedpacks.PackedPacksProfileTooltipMixin")) return "packed_packs";
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
