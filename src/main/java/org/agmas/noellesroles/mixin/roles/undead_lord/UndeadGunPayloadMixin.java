package org.agmas.noellesroles.mixin.roles.undead_lord;

import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.content.entity.UndeadEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让左轮等枪械可以击杀亡灵之主召唤的亡灵实体（服务端结算）。
 * 镜像 {@code PigeonGunPayloadMixin}：开枪数据包目标为亡灵时摧毁该亡灵并取消后续玩家击杀逻辑。
 */
@Mixin(GunShootPayload.Receiver.class)
public class UndeadGunPayloadMixin {
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void handleUndeadTarget(GunShootPayload payload, ServerPlayNetworking.Context context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (player.serverLevel().getEntity(payload.target()) instanceof UndeadEntity undead) {
            undead.shotByGun(player);
            ci.cancel();
        }
    }
}
