package org.agmas.noellesroles.mixin.halloween.pumpkin;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 替换南瓜精自己的默认任务收入，账本与实际余额使用同一个60金币值。 */
@Mixin(RoleMethodDispatcher.class)
public abstract class PumpkinTaskIncomeMixin {
    @ModifyVariable(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V",
            at = @At("STORE"), name = "reward")
    private static int pumpkin$taskIncome(int original, Player player, String quest, int streak, boolean parallel) {
        return HalloweenRuntime.isEnabled(player.level()) && PumpkinSpiritHandler.isSpirit(player) ? 60 : original;
    }
}
