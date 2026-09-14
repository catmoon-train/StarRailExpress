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

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自定义方块的方块实体：只记录「这个方块是哪个自定义方块」。
 *
 * <p>
 * 网络与性能上的考虑：
 * <ul>
 * <li>同步内容只有 id 字符串（几十字节），贴图 / 模型 / 事件都由客户端从已同步的配置里查，
 * 方块实体本身不做任何 ticking（没有注册 ticker），也没有每 tick 的网络包；</li>
 * <li>id 从物品组件经 {@link #applyImplicitComponents} 落到方块实体（放置瞬间完成），
 * 拾取 / 复制时经 {@link #collectImplicitComponents} 写回物品，因此挖下来再放回去
 * 仍是同一个自定义方块；</li>
 * <li>客户端渲染缓存（{@link #cachedVertices} 等）挂在方块实体上而不是全局表里，
 * 区块卸载时随方块实体一起回收，不会泄漏。</li>
 * </ul>
 */
public class CustomBlockEntity extends BlockEntity {

    private static final String TAG_ID = "custom_block_id";

    private String customBlockId = "";

    // ── 客户端渲染缓存（仅客户端使用，不持久化、不参与同步）──
    /** 上一次捕获顶点时用的展示状态。 */
    public BlockState cachedDisplayState = null;
    /** 上一次捕获顶点时的光照。 */
    public int cachedLight = -1;
    /** 展示模型的块局部顶点（含颜色 / UV / 光源 / 法线），每帧只按相机平移重放。 */
    public java.util.List<double[]> cachedVertices = null;

    public CustomBlockEntity(BlockPos pos, BlockState state) {
        super(io.wifi.starrailexpress.index.SREBlocks.CUSTOM_BLOCK_ENTITY, pos, state);
    }

    public String getCustomBlockId() {
        return customBlockId;
    }

    /** 该方块对应的配置数据（未同步 / id 未知返回 null）。 */
    public CustomBlockData data() {
        return CustomBlockLoader.get(customBlockId);
    }

    public void setCustomBlockId(String id) {
        String value = id == null ? "" : id;
        if (value.equals(this.customBlockId)) {
            return;
        }
        this.customBlockId = value;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        CustomBlockRuntime.onBlockChanged(this);
    }

    // ==================== 持久化 / 同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!customBlockId.isEmpty()) {
            tag.putString(TAG_ID, customBlockId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ID)) {
            customBlockId = tag.getString(TAG_ID);
        }
        // NBT 载入后（区块加载 / /sre:clone 搬过来的方块）按最终 id 重新登记靠近索引：
        // setLevel 比 loadAdditional 先跑，那时 id 还是空的，索引会漏掉这些方块。
        if (level != null && !level.isClientSide) {
            CustomBlockRuntime.updateIndex(this);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== 物品 ↔ 方块数据 ====================

    /**
     * 放置时由 {@code BlockItem.loadWithComponents} 调用：把物品上的自定义 id 写进方块实体。
     *
     * <p>
     * 注意这条路径<b>不会</b>经过 {@code loadAdditional}（{@code loadWithComponents} 只调
     * {@code applyImplicitComponents}），所以这里必须自己补一次索引登记，否则「用物品放下来的」
     * 自定义方块不会进靠近事件索引——靠近事件会静默失效。
     */
    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        String id = input.get(SREDataComponentTypes.CUSTOM_BLOCK_ID);
        if (id != null && !id.isBlank()) {
            customBlockId = id;
            if (level != null && !level.isClientSide) {
                CustomBlockRuntime.updateIndex(this);
            }
        }
    }

    /** 拾取 / 复制方块时把 id 写回物品。 */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!customBlockId.isEmpty()) {
            builder.set(SREDataComponentTypes.CUSTOM_BLOCK_ID, customBlockId);
        }
    }

    // ==================== 生命周期 ====================

    /**
     * 方块实体绑定到世界时（区块加载）按最新配置登记靠近索引、校正亮度与音效桶。
     *
     * <p>
     * 这里用 {@code setLevel} 而不是 {@code onLoad}：1.21 的 {@code BlockEntity} 没有
     * {@code onLoad} 钩子，{@code setLevel} 是「实体已知道自己在哪个世界」的第一个时机。
     */
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            CustomBlockRuntime.onBlockLoaded(this);
        }
    }

    @Override
    public void setRemoved() {
        CustomBlockRuntime.onBlockRemoved(this);
        super.setRemoved();
    }
}
