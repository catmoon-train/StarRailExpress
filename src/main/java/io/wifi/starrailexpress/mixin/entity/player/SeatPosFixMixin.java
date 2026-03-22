package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.block.MountableBlock;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class SeatPosFixMixin {
    @Inject(method = "dismountTo", at = @At("HEAD"), cancellable = true)
    public void stopRiding(double d, double e, double f, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        var lastPos = MountableBlock.lastPos.get(player.getUUID());
        if (lastPos != null) {
            if (lastPos.distanceTo(player.position()) < 5) {
                int lx = (int) lastPos.x();
                int ly = (int) lastPos.y();
                int lz = (int) lastPos.z();
                if (player.level().getBlockState(new BlockPos(lx, ly + 1, lz)).getBlock() instanceof MountableBlock) {
                    player.teleportTo(lastPos.x, lastPos.y + 2.25, lastPos.z);
                } else {
                    player.teleportTo(lastPos.x, lastPos.y + 0.75, lastPos.z);
                }
                // 移除记录,防止连续坐椅子时累积高度
                MountableBlock.lastPos.remove(player.getUUID());

                // 下座椅添加cooldown
            } else {
                var vec = player.position();
                player.teleportTo(vec.x, vec.y + 0.75, vec.z);
            }
            ci.cancel();

        }
        player.getCooldowns().addCooldown(TMMBlocks.ACACIA_BRANCH.asItem(), 10);
    }

}
