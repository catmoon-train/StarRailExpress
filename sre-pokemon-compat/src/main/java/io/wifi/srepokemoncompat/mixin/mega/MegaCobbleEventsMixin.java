package io.wifi.srepokemoncompat.mixin.mega;

import io.wifi.starrailexpress.compat.cobblemon.CobblemonMechanics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拦截新玩法效果，不取消事件总线，也不取消回退、捕获数据修正或战斗结束清理。 */
@Pseudo
@Mixin(targets = "com.github.yajatkaul.mega_showdown.event.CobbleEvents", remap = false)
public abstract class MegaCobbleEventsMixin {
    @Inject(method = {"formChanged", "dropShardPokemon", "pokemonSent", "ultraBurst", "dynamaxStarted",
            "zMovesUsed", "healedPokemons", "terrastallizationUsed", "hookBattlePre", "megaEvolution"},
            at = @At("HEAD"), cancellable = true, require = 10)
    private static void sre$gameplayEvent(CallbackInfo ci) {
        if (!CobblemonMechanics.enabled()) ci.cancel();
    }
}
