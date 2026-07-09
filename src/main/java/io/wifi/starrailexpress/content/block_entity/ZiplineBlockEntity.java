package io.wifi.starrailexpress.content.block_entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ZiplineBlockEntity extends BlockEntity {
    private static final String CONNECTIONS_KEY = "Connections";
    /** 1.0 时期使用绝对坐标保存连接，读档时迁移成相对偏移 */
    private static final String LEGACY_CONNECTIONS_KEY = "ConnectedPositions";

    /**
     * 连接以“相对本柱子的偏移”保存，而不是绝对坐标。
     * 整区复制（BlockCopyUtils.copyLayer）会原样搬运方块实体 NBT，只改写 x/y/z，
     * 绝对坐标会让复制出来的滑索反向连回模板区的柱子。
     */
    private final Set<Vec3i> connectionOffsets = new HashSet<>();
    /** 解析成绝对坐标的连接，渲染器每帧都要读。每次改动都整体换一个新集合，遍历中改动不会炸。 */
    @Nullable
    private Set<BlockPos> resolvedCache;

    public ZiplineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Set<BlockPos> getConnectedPositions() {
        Set<BlockPos> cached = resolvedCache;
        if (cached == null) {
            Set<BlockPos> positions = new LinkedHashSet<>(connectionOffsets.size());
            for (Vec3i offset : connectionOffsets) {
                positions.add(worldPosition.offset(offset));
            }
            cached = Collections.unmodifiableSet(positions);
            resolvedCache = cached;
        }
        return cached;
    }

    public void addConnection(BlockPos pos) {
        Vec3i offset = toOffset(pos);
        if (offset != null && connectionOffsets.add(offset)) {
            onConnectionsChanged();
        }
    }

    public void removeConnection(BlockPos pos) {
        Vec3i offset = toOffset(pos);
        if (offset != null && connectionOffsets.remove(offset)) {
            onConnectionsChanged();
        }
    }

    public void clearConnections() {
        if (!connectionOffsets.isEmpty()) {
            connectionOffsets.clear();
            onConnectionsChanged();
        }
    }

    private void onConnectionsChanged() {
        resolvedCache = null;
        setChanged();
        sync();
    }

    public boolean hasConnection(BlockPos pos) {
        Vec3i offset = toOffset(pos);
        return offset != null && connectionOffsets.contains(offset);
    }

    public boolean hasAnyConnection() {
        return !connectionOffsets.isEmpty();
    }

    @Nullable
    public BlockPos getNearestConnection(BlockPos from) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos connected : getConnectedPositions()) {
            double dist = connected.distSqr(from);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = connected;
            }
        }
        return nearest;
    }

    @Nullable
    private Vec3i toOffset(BlockPos pos) {
        Vec3i offset = pos.subtract(worldPosition);
        return offset.equals(Vec3i.ZERO) ? null : offset;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Vec3i offset : connectionOffsets) {
            list.add(new IntArrayTag(new int[] { offset.getX(), offset.getY(), offset.getZ() }));
        }
        tag.put(CONNECTIONS_KEY, list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        connectionOffsets.clear();
        resolvedCache = null;
        if (tag.contains(CONNECTIONS_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(CONNECTIONS_KEY, Tag.TAG_INT_ARRAY);
            for (int i = 0; i < list.size(); i++) {
                int[] offset = list.getIntArray(i);
                if (offset.length == 3) {
                    addOffset(new Vec3i(offset[0], offset[1], offset[2]));
                }
            }
        } else if (tag.contains(LEGACY_CONNECTIONS_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(LEGACY_CONNECTIONS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                addOffset(toOffset(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z"))));
            }
        }
    }

    private void addOffset(@Nullable Vec3i offset) {
        if (offset != null && !offset.equals(Vec3i.ZERO)) {
            connectionOffsets.add(offset);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
