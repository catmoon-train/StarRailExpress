package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.DreadlordClientFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 恐惧魔王浮起的方块，在原位置先当成空气，避免和浮起的模型叠在一起。 */
@Mixin(RenderChunkRegion.class)
public abstract class DreadlordFloatBlockMixin {
    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState noellesroles$hideLiftedBlock(BlockState original, BlockPos pos) {
        if (!original.isAir() && DreadlordClientFx.hides(pos.getX(), pos.getY(), pos.getZ())) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }
}
