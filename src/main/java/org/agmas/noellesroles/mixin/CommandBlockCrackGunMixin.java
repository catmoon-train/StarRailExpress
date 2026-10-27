package org.agmas.noellesroles.mixin;

import org.agmas.noellesroles.game.command_block.CommandBlockCracks;

import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 手枪打中命令方块裂缝时击碎它。不取消原射击，冷却和枪声照常。 */
@Mixin(GunShootPayload.Receiver.class)
public class CommandBlockCrackGunMixin {
    @Inject(method = "receive", at = @At("HEAD"))
    private void noellesroles$shatterCrack(GunShootPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer player = context.player();
        var target = player.serverLevel().getEntity(payload.target());
        if (!CommandBlockCracks.tryShatter(player, target) && payload.target() < 0) {
            CommandBlockCracks.tryShatter(player, CommandBlockCracks.alongLook(player, 20.0D));
        }
    }
}
