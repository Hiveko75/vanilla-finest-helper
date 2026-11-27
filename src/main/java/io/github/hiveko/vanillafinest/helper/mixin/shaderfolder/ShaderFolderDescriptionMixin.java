package io.github.hiveko.vanillafinest.helper.mixin.shaderfolder;

import java.lang.reflect.Method;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris Shader Folder shows a shader pack's own description (its shaders/lang file or pack.json) above the
 * tooltip set in config/iris_shader_folder.properties. For a pack that has a tooltip there, only that tooltip
 * is shown: the pack's own description is left out (Euphoria Patches' is three lines of version numbers and
 * links, and only in some languages). Packs without a tooltip rule, such as ones added by hand, keep their
 * own description. Used by Iris's shader list and the setup screen alike. Applied only with Shader Folder
 * in a version this was made for (see MixinPlugin and vanilla-finest-helper.modules.properties).
 */
@Mixin(targets = "com.spaceagle17.iris_shader_folder.functionality.ShaderTooltipSystem", remap = false)
public abstract class ShaderFolderDescriptionMixin {
    @Shadow
    private List<?> tooltipRules;

    private static Method vanillaFinest$pattern;
    private static Method vanillaFinest$matches;

    @Inject(method = "getDescription", at = @At("RETURN"), cancellable = true)
    private void vanillaFinest$onlyOwnTooltip(String shaderName, CallbackInfoReturnable<String> cir) {
        String description = cir.getReturnValue();
        if (description == null || description.isEmpty() || shaderName == null || tooltipRules == null) return;
        try {
            for (Object rule : tooltipRules) {
                if (rule == null) continue;
                if (vanillaFinest$pattern == null) {
                    Method m = rule.getClass().getMethod("getShaderPattern");
                    m.setAccessible(true);
                    vanillaFinest$pattern = m;
                }
                if (vanillaFinest$matches == null) {
                    vanillaFinest$matches = Class.forName("com.spaceagle17.iris_shader_folder.util.ShaderPatternUtil")
                            .getMethod("matchesPattern", String.class, String.class);
                }
                Object pattern = vanillaFinest$pattern.invoke(rule);
                if (pattern instanceof String p
                        && Boolean.TRUE.equals(vanillaFinest$matches.invoke(null, shaderName, p))) {
                    cir.setReturnValue("");
                    return;
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            // the pack's own description stays then
        }
    }
}
