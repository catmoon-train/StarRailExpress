package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.content.block.TicketGateBlock;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class TicketGateBlockEntity extends SyncingBlockEntity {
    private static final int AUTO_CLOSE_TICKS = 40; // 2秒

    private String ticketId = "";
    private String ticketName = "";
    private int openTicks = 0;

    public TicketGateBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.TICKET_GATE, pos, state);
    }

    public String getTicketId() {
        return ticketId == null ? "" : ticketId;
    }

    public String getTicketName() {
        return ticketName == null ? "" : ticketName;
    }

    public void setTicketInfo(String ticketId, String ticketName) {
        this.ticketId = ticketId == null ? "" : ticketId;
        this.ticketName = ticketName == null ? "" : ticketName;
        sync();
    }

    public boolean hasTicket() {
        return !getTicketId().isBlank();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TicketGateBlockEntity entity) {
        // 门打开后自动倒计时关闭
        if (state.getValue(DoorBlock.OPEN)) {
            entity.openTicks++;
            if (entity.openTicks >= AUTO_CLOSE_TICKS) {
                entity.openTicks = 0;
                // 关闭门（原版方式）
                BlockState lowerState = state.getValue(TicketGateBlock.HALF) == DoubleBlockHalf.LOWER
                        ? state
                        : level.getBlockState(pos.below());
                if (lowerState.getBlock() instanceof TicketGateBlock) {
                    level.setBlock(pos, lowerState.setValue(DoorBlock.OPEN, false),
                            Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
                    level.setBlock(pos.above(), level.getBlockState(pos.above()).setValue(DoorBlock.OPEN, false),
                            Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        } else {
            entity.openTicks = 0;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);
        tag.putString("TicketId", getTicketId());
        tag.putString("TicketName", getTicketName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        this.ticketId = tag.getString("TicketId");
        this.ticketName = tag.getString("TicketName");
    }
}
