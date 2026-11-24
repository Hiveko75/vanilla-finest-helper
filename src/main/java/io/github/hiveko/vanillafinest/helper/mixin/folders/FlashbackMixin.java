package io.github.hiveko.vanillafinest.helper.mixin.folders;

import java.nio.file.Path;

import io.github.hiveko.vanillafinest.helper.folders.Folders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Flashback saves and lists replays in Flashback.getReplayFolder() (its data folder + "replays"); every
 * screen that saves, lists or combines replays asks it. Here it returns the folder from the helper's
 * settings when one is set. The setting is read each time, so a change counts at once. Not applied
 * when Flashback Settings is installed (see MixinPlugin), which does the same with its own setting.
 */
@Mixin(targets = "com.moulberry.flashback.Flashback", remap = false)
public abstract class FlashbackMixin {
    @Inject(method = "getReplayFolder", at = @At("RETURN"), cancellable = true)
    private static void vanillaFinest$replayFolder(CallbackInfoReturnable<Path> cir) {
        Path folder = Folders.override(Folders.FLASHBACK);
        if (folder != null) cir.setReturnValue(folder);
    }
}
