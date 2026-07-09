package io.wifi.starrailexpress.mixin.client.hakoniwa;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.HakoniwaVisionClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 箱庭视野：vanilla 区块网格构建路径的方块剔除 —— 处于切割盒内的方块
 * 在构建网格时被视为空气（sodium 路径见 compat/sodium 的 WorldSlice mixin）。
 */
@Mixin(RenderChunkRegion.class)
public abstract class HakoniwaRenderChunkRegionMixin {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState sre$hakoniwaCutBlock(BlockState original, BlockPos pos) {
        if (!original.isAir()
                && HakoniwaVisionClientHandle.shouldHideBlock(original, pos.getX(), pos.getY(), pos.getZ())) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }
}
