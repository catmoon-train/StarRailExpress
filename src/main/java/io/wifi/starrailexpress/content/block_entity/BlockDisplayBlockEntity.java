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

package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.SREDisplayBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 方块展示方块的方块实体：NBT 与原版 {@code minecraft:block_display} 一致，展示内容放在
 * {@code block_state} 里（和原版一样是完整方块状态，含 Properties）。
 */
public class BlockDisplayBlockEntity extends DisplayBlockEntityBase {

    public static final String TAG_BLOCK_STATE = "block_state";

    /** 没有 block_state 时渲染出来的占位方块，保证刚放下的方块可见、可被右键。 */
    public static final BlockState DEFAULT_BLOCK_STATE = Blocks.STONE.defaultBlockState();

    public BlockDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(SREDisplayBlocks.BLOCK_DISPLAY_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected List<String> contentKeys() {
        return List.of(TAG_BLOCK_STATE);
    }

    @Override
    protected void contentFillDefaults(CompoundTag tag) {
        if (!tag.contains(TAG_BLOCK_STATE, Tag.TAG_COMPOUND)) {
            tag.put(TAG_BLOCK_STATE, NbtUtils.writeBlockState(DEFAULT_BLOCK_STATE));
        }
    }

    /** 解析结果的缓存：渲染器每帧都会问一次，别每帧都去解一遍 NBT。 */
    private BlockState cachedBlockState;
    private int cachedRevision = Integer.MIN_VALUE;

    /**
     * 展示的方块状态；解析失败、缺失或指向空气时回落到石头。
     * 结果按 {@link #getDataRevision()} 缓存，数据一变就重新解析。
     */
    public BlockState getDisplayedBlockState() {
        if (this.cachedBlockState != null && this.cachedRevision == getDataRevision()) {
            return this.cachedBlockState;
        }
        CompoundTag tag = getTag();
        BlockState state = DEFAULT_BLOCK_STATE;
        if (tag.contains(TAG_BLOCK_STATE, Tag.TAG_COMPOUND)) {
            BlockState parsed = NbtUtils.readBlockState(blockHolderGetter(), tag.getCompound(TAG_BLOCK_STATE));
            if (!parsed.isAir()) {
                state = parsed;
            }
        }
        this.cachedBlockState = state;
        this.cachedRevision = getDataRevision();
        return state;
    }

    private HolderGetter<Block> blockHolderGetter() {
        Level level = getLevel();
        if (level != null) {
            return level.holderLookup(Registries.BLOCK);
        }
        return BuiltInRegistries.BLOCK.asLookup();
    }

    /** 服务端保存时校正 block_state，避免界面传来不存在的方块。 */
    @Override
    protected void contentSanitize() {
        CompoundTag tag = getTag();
        if (!tag.contains(TAG_BLOCK_STATE, Tag.TAG_COMPOUND)) {
            return;
        }
        BlockState state = NbtUtils.readBlockState(blockHolderGetter(), tag.getCompound(TAG_BLOCK_STATE));
        tag.put(TAG_BLOCK_STATE, NbtUtils.writeBlockState(state.isAir() ? DEFAULT_BLOCK_STATE : state));
    }
}
