package org.agmas.noellesroles.mixin.roles.poisoner;


import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(SREPlayerPoisonComponent.class)
public abstract class PoisonerNoPoisonMixin {

    @Shadow private Player player;
    @Shadow public int poisonTicks;
    @Shadow public UUID poisoner;

    /**
     * 阻止毒师被施加中毒（setPoisonTicks）
     */
    @Inject(method = "setPoisonTicks", at = @At("HEAD"), cancellable = true)
    private void poisonerNoPoison(int ticks, UUID poisoner, CallbackInfo ci) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld.isRole(this.player, ModRoles.POISONER) ) {
            ci.cancel();
        }
    }

    /**
     * 阻止毒师进入 serverTick（potionTick）中毒倒计时
     * 复用故障机器人模式：在 tick 头部直接清零中毒计时器
     */
    @Inject(method = "serverTick", at = @At("HEAD"))
    private void poisonerNoPoisonTick(CallbackInfo ci) {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(this.player.level());
        if (gameWorld != null && gameWorld.isRole(this.player, ModRoles.POISONER)) {
            if (this.poisonTicks > 0) {
                this.poisonTicks = -1;
                this.poisoner = null;
            }
        }
    }
}
