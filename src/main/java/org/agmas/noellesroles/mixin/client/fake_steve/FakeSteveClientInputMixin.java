package org.agmas.noellesroles.mixin.client.fake_steve;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.agmas.noellesroles.client.FakeSteveClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies server-owned movement after physical keyboard state has been sampled. */
@Mixin(KeyboardInput.class)
public abstract class FakeSteveClientInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void noellesroles$applyFakeSteveInput(boolean sneaking, float sneakSpeed,
            CallbackInfo ci) {
        FakeSteveClient.applyAiInput((Input) (Object) this);
    }
}
