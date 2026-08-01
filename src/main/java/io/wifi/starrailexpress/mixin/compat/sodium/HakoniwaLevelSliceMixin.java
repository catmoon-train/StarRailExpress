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

package io.wifi.starrailexpress.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.client.HakoniwaVisionClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 箱庭视野：sodium（0.6.x/0.8.x，section 快照类为 LevelSlice）区块网格构建路径的方块剔除
 * （对应 vanilla 的 {@code HakoniwaRenderChunkRegionMixin}）。切割盒内的方块被视为空气。
 */
@Mixin(LevelSlice.class)
public abstract class HakoniwaLevelSliceMixin {

    @ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"), remap = false)
    private BlockState sre$hakoniwaCutBlock(BlockState original, int x, int y, int z) {
        if (!original.isAir() && HakoniwaVisionClientHandle.shouldHideBlock(original, x, y, z)) {
            return Blocks.AIR.defaultBlockState();
        }
        return original;
    }
}
