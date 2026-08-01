/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
