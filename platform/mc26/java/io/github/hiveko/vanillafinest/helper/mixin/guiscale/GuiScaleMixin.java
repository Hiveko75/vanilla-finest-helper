package io.github.hiveko.vanillafinest.helper.mixin.guiscale;

import io.github.hiveko.vanillafinest.helper.guiscale.GuiScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GUI scale module: picks the automatic GUI scale before Minecraft applies it ({@link GuiScale}). */
@Mixin(Minecraft.class)
public abstract class GuiScaleMixin {
    @Inject(method = "resizeGui", at = @At("HEAD"))
    private void vanillaFinestHelper$automaticGuiScale(CallbackInfo ci) {
        GuiScale.beforeResize((Minecraft) (Object) this);
    }
}
