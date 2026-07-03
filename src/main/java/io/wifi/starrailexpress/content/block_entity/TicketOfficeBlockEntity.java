package io.wifi.starrailexpress.content.block_entity;

import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class TicketOfficeBlockEntity extends SyncingBlockEntity {
    private int price = 1;
    private String ticketName = "";
    private ShopEntry.Currency currency = ShopEntry.Currency.MONEY;
    private int uses = 1;
    private UUID ticketId = UUID.randomUUID();

    public TicketOfficeBlockEntity(BlockPos pos, BlockState state) {
        super(TMMBlockEntities.TICKET_OFFICE, pos, state);
    }

    public int getPrice() {
        return price;
    }

    public String getTicketName() {
        return ticketName == null || ticketName.isBlank() ? "Admission Ticket" : ticketName;
    }

    public ShopEntry.Currency getCurrency() {
        return currency == null ? ShopEntry.Currency.MONEY : currency;
    }

    public int getUses() {
        return uses;
    }

    public UUID getTicketId() {
        if (ticketId == null) {
            ticketId = UUID.randomUUID();
        }
        return ticketId;
    }

    public CompoundTag toConfigTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Price", price);
        tag.putString("TicketName", getTicketName());
        tag.putString("Currency", getCurrency().serializedName());
        tag.putInt("Uses", uses);
        tag.putString("TicketId", getTicketId().toString());
        return tag;
    }

    public void loadConfig(CompoundTag tag) {
        this.price = Math.max(0, tag.getInt("Price"));
        this.ticketName = tag.getString("TicketName");
        this.currency = ShopEntry.Currency.fromSerializedName(tag.getString("Currency"));
        this.uses = tag.contains("Uses") ? tag.getInt("Uses") : 1;
        String id = tag.getString("TicketId");
        if (!id.isBlank()) {
            try {
                this.ticketId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                this.ticketId = UUID.randomUUID();
            }
        }
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.saveAdditional(tag, registryLookup);
        tag.merge(toConfigTag());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registryLookup) {
        super.loadAdditional(tag, registryLookup);
        this.price = Math.max(0, tag.getInt("Price"));
        this.ticketName = tag.getString("TicketName");
        this.currency = ShopEntry.Currency.fromSerializedName(tag.getString("Currency"));
        this.uses = tag.contains("Uses") ? tag.getInt("Uses") : 1;
        String id = tag.getString("TicketId");
        if (!id.isBlank()) {
            try {
                this.ticketId = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                this.ticketId = UUID.randomUUID();
            }
        }
    }
}
