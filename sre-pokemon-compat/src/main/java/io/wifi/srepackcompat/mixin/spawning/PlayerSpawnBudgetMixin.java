package io.wifi.srepackcompat.mixin.spawning;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.srepackcompat.cobblemon.CobblemonSpawnBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner", remap = false)
public class PlayerSpawnBudgetMixin {
    @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void sre$serverSwitch(CallbackInfo ci) {
        if (!CobblemonSpawnBudget.enabled()) ci.cancel();
    }

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "FIELD",
            target = "Lcom/cobblemon/mod/common/api/spawning/spawner/PlayerSpawner;tickTimerMultiplier:F"), require = 1)
    private float sre$naturalSpawnFrequency(float original) { return CobblemonSpawnBudget.scale(original); }
}
