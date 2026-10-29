package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只向存活的恐惧魔王同步旁观者实体，让冒险模式客户端也能绘制死者魂魄。 */
@Mixin(ServerPlayer.class)
public abstract class DreadlordSoulTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("RETURN"), cancellable = true)
    private void noellesroles$trackSouls(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer soul = (ServerPlayer) (Object) this;
        // 原版只向旁观者广播旁观者；保留其它广播规则，仅为本职业增加例外。
        if (!cir.getReturnValueZ() && soul.isSpectator() && soul != viewer
                && soul.level() == viewer.level()
                && GameUtils.isPlayerAliveAndSurvival(viewer)
                && DreadlordRole.isDreadlord(viewer)) {
            cir.setReturnValue(true);
        }
    }
}
