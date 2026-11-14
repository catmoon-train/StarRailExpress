package org.agmas.noellesroles.mixin.client.roles.ma_chen_xu;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.SackGhostBlockChanges;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原生客户端方块更新优先于里世界外观；不拦截包、不修改服务端或项目自有类。 */
@Mixin(ClientLevel.class)
public abstract class SackGhostBlockUpdateMixin {
    @Inject(method = "setServerVerifiedBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)V", at = @At("HEAD"))
    private void sackGhost$serverUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        SackGhostBlockChanges.beforeNativeUpdate((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "syncBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void sackGhost$predictionSync(BlockPos pos, BlockState state, Vec3 playerPosition, CallbackInfo ci) {
        SackGhostBlockChanges.beforeNativeUpdate((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void sackGhost$localUpdate(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        SackGhostBlockChanges.beforeNativeUpdate((ClientLevel) (Object) this, pos);
    }
}
