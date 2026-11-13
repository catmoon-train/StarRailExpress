package org.agmas.noellesroles.game.minigame.roulette;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 待领取补给保存在世界而非赌桌，拆桌、断线或卸载区块不会删除已分配的物品。 */
public final class RouletteSupply extends SavedData {
    private final Map<UUID, ArrayDeque<RouletteItem>> pending = new HashMap<>();
    private final Map<UUID, Long> heldUntil = new HashMap<>();
    public static RouletteSupply get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RouletteSupply::new, RouletteSupply::load, null), "sre_roulette_supply");
    }
    public int pending(UUID player) { return pending.getOrDefault(player, new ArrayDeque<>()).size(); }
    public int reserve(ServerPlayer player, int requested) {
        int amount = RouletteItems.grantCount(RouletteItems.count(player), pending(player.getUUID()), RouletteBalance.supply(requested));
        if (amount == 0) {
            player.displayClientMessage(Component.translatable("roulette.hint.supply_full"), true);
            return 0;
        }
        ArrayDeque<RouletteItem> queue = pending.computeIfAbsent(player.getUUID(), ignored -> new ArrayDeque<>());
        for (int i = 0; i < amount; i++) queue.addLast(RouletteItem.values()[player.getRandom().nextInt(RouletteItem.values().length)]);
        heldUntil.put(player.getUUID(), player.serverLevel().getServer().overworld().getGameTime() + RoulettePhase.SUPPLY.duration());
        setDirty();
        return amount;
    }
    /** 单件原子入包：失败时保留队首；永不调用会在背包满时掉落物品的 Player.addItem。 */
    public RouletteItem deliver(ServerPlayer player) {
        ArrayDeque<RouletteItem> queue = pending.get(player.getUUID());
        if (queue == null || queue.isEmpty() || RouletteItems.count(player) >= RouletteItems.CAPACITY) return null;
        RouletteItem kind = queue.peekFirst();
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) return null;
        player.getInventory().setItem(slot, RouletteItems.stack(kind));
        queue.removeFirst();
        if (queue.isEmpty()) { pending.remove(player.getUUID()); heldUntil.remove(player.getUUID()); }
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable("roulette.result.received", Component.translatable(kind.titleKey())), true);
        setDirty();
        return kind;
    }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.overworld().getGameTime() % 20 != 0) return;
            RouletteSupply data = get(server.overworld());
            for (UUID id : java.util.List.copyOf(data.pending.keySet())) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.isAlive() && !player.isSpectator()
                        && !org.agmas.noellesroles.game.roles.neutral.roulette_devil.RouletteDevilSessions.inDuel(id)
                        && server.overworld().getGameTime() >= data.heldUntil.getOrDefault(id, 0L)) data.deliver(player);
            }
        });
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        pending.forEach((id, items) -> {
            CompoundTag row = new CompoundTag(); row.putUUID("Player", id);
            row.putIntArray("Items", items.stream().map(RouletteItem::ordinal).toList()); list.add(row);
        });
        tag.put("Pending", list);
        return tag;
    }
    private static RouletteSupply load(CompoundTag tag, HolderLookup.Provider registries) {
        RouletteSupply result = new RouletteSupply();
        for (Tag entry : tag.getList("Pending", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            if (!row.hasUUID("Player")) continue;
            ArrayDeque<RouletteItem> queue = new ArrayDeque<>();
            for (int ordinal : row.getIntArray("Items")) {
                RouletteItem kind = RouletteItem.byOrdinal(ordinal);
                if (kind != null && queue.size() < RouletteItems.CAPACITY) queue.add(kind);
            }
            if (!queue.isEmpty()) result.pending.put(row.getUUID("Player"), queue);
        }
        return result;
    }
}
