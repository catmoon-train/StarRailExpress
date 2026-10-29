package org.agmas.noellesroles.mixin.halloween;

import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.HalloweenCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 沿用真实任务完成入口，场景任务和普通任务均计入契约进度。 */
@Mixin(RoleMethodDispatcher.class)
public abstract class HalloweenTaskMixin {
    @Inject(method = "callOnFinishQuest(Lnet/minecraft/world/entity/player/Player;Ljava/lang/String;IZ)V", at = @At("TAIL"))
    private static void halloween$quest(Player player, String quest, int streak, boolean parallel, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            HalloweenCombat.onTaskCompleted(serverPlayer);
            org.agmas.noellesroles.game.halloween.HalloweenTaskRewards.onTaskCompleted(serverPlayer);
            org.agmas.noellesroles.game.halloween.pumpkin.PumpkinSpiritHandler.onTaskCompleted(serverPlayer);
        }
    }
}
