package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class TicketGateBlockEntity extends SyncingBlockEntity {
    private String ticketId = "";

    public TicketGateBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.TICKET_GATE, pos, state);
    }

    public String getTicketId() {
        return ticketId == null ? "" : ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId == null ? "" : ticketId;
        sync();
    }

    public boolean hasTicket() {
        return !getTicketId().isBlank();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TicketGateBlockEntity entity) {
        // 原版 DoorBlock 的 neighborChanged 已处理红石开关，此处无需额外逻辑
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);
        tag.putString("TicketId", getTicketId());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        this.ticketId = tag.getString("TicketId");
    }
}
