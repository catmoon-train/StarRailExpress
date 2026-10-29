package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.agmas.noellesroles.game.halloween.HalloweenCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 血锯锁定不能仅靠客户端禁用按键；原版交互包同样在服务端拒绝。 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class HalloweenPlayerActionsMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = {"handleUseItem", "handleUseItemOn", "handleInteract", "handlePlayerAction",
            "handleSetCarriedItem", "handleContainerClick", "handleContainerButtonClick"},
            at = @At("HEAD"), cancellable = true)
    private void halloween$lock(CallbackInfo ci) {
        // Netty 首次进入时让原方法完成线程调度；主线程重入后再读取局内状态。
        if (!player.server.isSameThread() || !HalloweenCombat.isActionLocked(player)) return;
        player.containerMenu.broadcastFullState();
        ci.cancel();
    }
}
