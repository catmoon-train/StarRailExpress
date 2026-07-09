package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 镜子方块的配置载体。数据本身很轻，只是让建图者能逐面调整反射体积。
 *
 * <p>{@code depth} 是反射向镜面后方延伸的格数；{@code lateralMargin} 是反射体积在镜面平面上
 * 相对镜面自身包围盒向四周外扩的格数（视线会随距离发散，所以侧向要比镜面本身宽）。
 */
public class MirrorBlockEntity extends BlockEntity {

    public static final int DEFAULT_DEPTH = 20;
    public static final int DEFAULT_LATERAL_MARGIN = 5;

    private static final int MAX_DEPTH = 48;
    private static final int MAX_LATERAL_MARGIN = 16;

    private int depth = DEFAULT_DEPTH;
    private int lateralMargin = DEFAULT_LATERAL_MARGIN;

    public MirrorBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.MIRROR, pos, state);
    }

    public int getDepth() {
        return this.depth;
    }

    public int getLateralMargin() {
        return this.lateralMargin;
    }

    public void setDepth(int depth) {
        this.depth = Mth.clamp(depth, 2, MAX_DEPTH);
        setChanged();
        syncToClient();
    }

    public void setLateralMargin(int lateralMargin) {
        this.lateralMargin = Mth.clamp(lateralMargin, 0, MAX_LATERAL_MARGIN);
        setChanged();
        syncToClient();
    }

    private void syncToClient() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Depth", this.depth);
        tag.putInt("LateralMargin", this.lateralMargin);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Depth")) {
            this.depth = Mth.clamp(tag.getInt("Depth"), 2, MAX_DEPTH);
        }
        if (tag.contains("LateralMargin")) {
            this.lateralMargin = Mth.clamp(tag.getInt("LateralMargin"), 0, MAX_LATERAL_MARGIN);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
