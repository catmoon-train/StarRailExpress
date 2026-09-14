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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 物品展示方块的方块实体：NBT 与原版 {@code minecraft:item_display} 一致。
 *
 * <p>原版键：{@code item}（物品堆，含 id / count / components）、{@code item_display}
 * （{@code ItemDisplayContext}，如 fixed / ground / gui / head / thirdperson_righthand …）。
 */
public class ItemDisplayBlockEntity extends DisplayBlockEntityBase {

    public static final String TAG_ITEM = "item";
    public static final String TAG_ITEM_DISPLAY = "item_display";

    /** 原版缺席时是 {@code NONE}，但那个会让物品贴着方块角落，作为展示默认值不直观，所以默认 fixed。 */
    public static final ItemDisplayContext DEFAULT_TRANSFORM = ItemDisplayContext.FIXED;
    /** 没有 item 键时渲染的占位物品，保证刚放下的方块可见、可右键。 */
    private static final String DEFAULT_ITEM_ID = "minecraft:stone";

    public ItemDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(SREDisplayBlocks.ITEM_DISPLAY_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected List<String> contentKeys() {
        return List.of(TAG_ITEM, TAG_ITEM_DISPLAY);
    }

    @Override
    protected void contentFillDefaults(CompoundTag tag) {
        if (!tag.contains(TAG_ITEM, Tag.TAG_COMPOUND)) {
            // 手写一个最小物品 NBT（{id, count}），不依赖注册表查询
            CompoundTag item = new CompoundTag();
            item.putString("id", DEFAULT_ITEM_ID);
            item.putInt("count", 1);
            tag.put(TAG_ITEM, item);
        }
        if (!tag.contains(TAG_ITEM_DISPLAY, Tag.TAG_STRING)) {
            tag.putString(TAG_ITEM_DISPLAY, DEFAULT_TRANSFORM.getSerializedName());
        }
    }

    /** 解析 item 键；解析不出来就是空堆（渲染器会跳过）。 */
    public ItemStack getDisplayedItem(HolderLookup.Provider registries) {
        if (this.cachedItemRevision == getDataRevision() && this.cachedItem != null) {
            return this.cachedItem;
        }
        ItemStack parsed = ItemStack.EMPTY;
        Tag tag = getTag().get(TAG_ITEM);
        if (tag != null) {
            parsed = ItemStack.parse(registries, tag).orElse(ItemStack.EMPTY);
        }
        this.cachedItem = parsed;
        this.cachedItemRevision = getDataRevision();
        return parsed;
    }

    /** 物品在展示时套用的变换（item_display）。 */
    public ItemDisplayContext getItemTransform() {
        if (!getTag().contains(TAG_ITEM_DISPLAY, Tag.TAG_STRING)) {
            return DEFAULT_TRANSFORM;
        }
        return ItemDisplayContext.CODEC.parse(NbtOps.INSTANCE, getTag().get(TAG_ITEM_DISPLAY))
                .resultOrPartial()
                .orElse(DEFAULT_TRANSFORM);
    }

    /** 物品解析开销不小（要查注册表 + 组件），按数据版本缓存。 */
    private ItemStack cachedItem;
    private int cachedItemRevision = Integer.MIN_VALUE;
}
