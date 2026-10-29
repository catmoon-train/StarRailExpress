package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.agmas.noellesroles.client.RoadRollerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 隐藏压路机经过处的方块网格，真实地形及碰撞保持不变。 */
@Mixin(RenderChunkRegion.class)
public abstract class RoadRollerBlockMixin {
    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState noellesroles$roadRollerBlock(BlockState original, BlockPos pos) {
        return RoadRollerClient.hides(pos.getX(), pos.getY(), pos.getZ()) ? Blocks.AIR.defaultBlockState() : original;
    }

    @ModifyReturnValue(method = "getFluidState", at = @At("RETURN"))
    private FluidState noellesroles$roadRollerFluid(FluidState original, BlockPos pos) {
        return RoadRollerClient.hides(pos.getX(), pos.getY(), pos.getZ()) ? Fluids.EMPTY.defaultFluidState() : original;
    }
}
