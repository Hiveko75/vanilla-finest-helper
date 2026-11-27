package io.github.hiveko.vanillafinest.helper.mixin.packedpacks;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.hiveko.vanillafinest.helper.setup.SetupTexts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Packed Packs keeps one description per profile (config/packed_packs/profiles/, one language only). Where its
 * profile list shows that description as a tooltip, each line is passed through
 * {@link SetupTexts#profileDescription(String)}, which gives the translation from the helper's texts
 * ({@code "profile.descriptions"} in {@code <lang>.json}, also from the config folder) or leaves it as it is.
 * Only the shown tooltip changes: the profile itself, and its edit form, keep the English text, so it is never
 * saved translated. Applied only with Packed Packs in a version this was made for (see MixinPlugin and
 * vanilla-finest-helper.modules.properties).
 */
@Mixin(targets = "io.github.fishstiz.packed_packs.gui.components.ProfileList$Entry", remap = false)
public abstract class PackedPacksProfileTooltipMixin {
    @ModifyExpressionValue(method = "updateTooltip", at = @At(value = "INVOKE",
            target = "Lio/github/fishstiz/packed_packs/config/Profile;getDescription()Ljava/lang/String;"))
    private String vanillaFinest$translate(String description) {
        return SetupTexts.profileDescription(description);
    }
}
