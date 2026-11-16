package io.wifi.srepackcompat.mixin.spawning;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.wifi.srepackcompat.cobblemon.CobblemonSpawnBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.api.spawning.spawner.Spawner", remap = false)
public interface SpawnPopulationBudgetMixin {
    // 上游取全局/局部上限的较大值，必须在 max 之后缩放，避免另一项绕过限制。
    @ModifyExpressionValue(method = "calculateSpawnActionsForArea", at = @At(value = "INVOKE",
            target = "Ljava/lang/Math;max(FF)F"), require = 1)
    default float sre$populationBudget(float original) {
        return CobblemonSpawnBudget.enabled() ? CobblemonSpawnBudget.scale(original) : 0F;
    }
}
