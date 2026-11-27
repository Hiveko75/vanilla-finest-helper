package io.github.hiveko.vanillafinest.helper.mixin.shaderfolder;

import io.github.hiveko.vanillafinest.helper.setup.SetupTexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris Shader Folder's tooltip rules (config/iris_shader_folder.properties) hold one text per shader, in one
 * language. Each rule's text is passed through {@link SetupTexts#shaderTooltip(String)}, which gives the
 * translation from the helper's texts ({@code "shader.tooltips"} in {@code <lang>.json}, also from the config
 * folder) or leaves it as it is. Used by Iris's shader list and the setup screen alike. Applied only with
 * Shader Folder in a version this was made for (see MixinPlugin and vanilla-finest-helper.modules.properties).
 */
@Mixin(targets = "com.spaceagle17.iris_shader_folder.functionality.ShaderTooltipSystem$TooltipRule", remap = false)
public abstract class ShaderFolderTooltipMixin {
    @Inject(method = "getTooltipText", at = @At("RETURN"), cancellable = true)
    private void vanillaFinest$translate(CallbackInfoReturnable<String> cir) {
        String text = cir.getReturnValue();
        String translated = SetupTexts.shaderTooltip(text);
        if (translated != null && !translated.equals(text)) cir.setReturnValue(translated);
    }
}
