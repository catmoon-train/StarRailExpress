package io.wifi.starrailexpress.mixin.world;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wifi.starrailexpress.scenery.server.SceneAssetServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class SceneAssetDirtyMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"),
            require = 0)
    private void sre$capturePreviousSceneBlock(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir,
            @Share("sre$previousState") LocalRef<BlockState> previousState) {
        previousState.set(((Level) (Object) this).getBlockState(pos));
    }

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"),
            require = 0)
    private void sre$markSceneAssetDirty(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir,
            @Share("sre$previousState") LocalRef<BlockState> previousState) {
        BlockState previous = previousState.get();
        if (cir.getReturnValueZ()
                && previous != state
                && (previous == null || !previous.equals(state))
                && (Object) this instanceof ServerLevel level) {
            SceneAssetServer.markBlockChanged(level, pos);
        }
    }
}
