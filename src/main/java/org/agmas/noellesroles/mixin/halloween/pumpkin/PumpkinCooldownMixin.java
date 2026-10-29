package org.agmas.noellesroles.mixin.halloween.pumpkin;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 统一技能组件原 API 每 tick 递减，南瓜精需要双端暂停冻结冷却。 */
@Mixin(SREAbilityPlayerComponent.class)
public abstract class PumpkinCooldownMixin {
    @Shadow @Final private Player player;
    @Inject(method = {"serverTick", "clientTick"}, at = @At("HEAD"), cancellable = true)
    private void pumpkin$freeze(CallbackInfo ci) {
        if (HalloweenRuntime.isEnabled(player.level()) && PumpkinSpiritHandler.isSpirit(player)
                && SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) ci.cancel();
    }
}
