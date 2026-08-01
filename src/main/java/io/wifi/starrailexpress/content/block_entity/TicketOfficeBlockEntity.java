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

import io.wifi.starrailexpress.index.TMMBlockEntities;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TicketOfficeBlockEntity extends SyncingBlockEntity {
    private int price = 1;
    private String ticketName = "";
    private ShopEntry.Currency currency = ShopEntry.Currency.MONEY;
    private int uses = 1;
    /** 限购次数，-1 表示不限购（默认） */
    private int maxPurchases = -1;
    private UUID ticketId = UUID.randomUUID();
    /** 当前游戏内每个玩家的购买计数（不持久化，游戏结束时清空） */
    private final Map<UUID, Integer> playerPurchaseCounts = new HashMap<>();

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

    public int getMaxPurchases() {
        return maxPurchases;
    }

    public UUID getTicketId() {
        if (ticketId == null) {
            ticketId = UUID.randomUUID();
        }
        return ticketId;
    }

    /** 检查玩家是否还能购买 */
    public boolean canPurchase(ServerPlayer player) {
        if (maxPurchases < 0) return true;
        return playerPurchaseCounts.getOrDefault(player.getUUID(), 0) < maxPurchases;
    }

    /** 返回剩余购买次数，-1 表示不限购 */
    public int getRemainingPurchases(ServerPlayer player) {
        if (maxPurchases < 0) return -1;
        return Math.max(0, maxPurchases - playerPurchaseCounts.getOrDefault(player.getUUID(), 0));
    }

    /** 记录一次购买 */
    public void recordPurchase(ServerPlayer player) {
        if (maxPurchases < 0) return;
        playerPurchaseCounts.merge(player.getUUID(), 1, Integer::sum);
    }

    /** 重置所有购买计数（游戏结束时调用） */
    public void resetPurchaseCounts() {
        playerPurchaseCounts.clear();
    }

    public CompoundTag toConfigTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Price", price);
        tag.putString("TicketName", getTicketName());
        tag.putString("Currency", getCurrency().serializedName());
        tag.putInt("Uses", uses);
        tag.putInt("MaxPurchases", maxPurchases);
        tag.putString("TicketId", getTicketId().toString());
        return tag;
    }

    public void loadConfig(CompoundTag tag) {
        this.price = Math.max(0, tag.getInt("Price"));
        this.ticketName = tag.getString("TicketName");
        this.currency = ShopEntry.Currency.fromSerializedName(tag.getString("Currency"));
        this.uses = tag.contains("Uses") ? tag.getInt("Uses") : 1;
        this.maxPurchases = tag.contains("MaxPurchases") ? tag.getInt("MaxPurchases") : -1;
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
        this.maxPurchases = tag.contains("MaxPurchases") ? tag.getInt("MaxPurchases") : -1;
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
