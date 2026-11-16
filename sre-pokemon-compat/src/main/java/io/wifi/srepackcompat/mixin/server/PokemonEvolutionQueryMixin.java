package io.wifi.srepackcompat.mixin.server;

import io.wifi.srepackcompat.cobblemon.EvolutionRequirementQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只替换存在性查询，不改变行走计数、进化判定或数据同步。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.pokemon.Pokemon", remap = false)
public class PokemonEvolutionQueryMixin {
    @Inject(method = "hasBlocksTraveledRequirement()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sre$allocationFreeRequirementQuery(CallbackInfoReturnable<Boolean> cir) {
        Boolean result = EvolutionRequirementQuery.query(this);
        if (result != null) cir.setReturnValue(result);
    }
}
