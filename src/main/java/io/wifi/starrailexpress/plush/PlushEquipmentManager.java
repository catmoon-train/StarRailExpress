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

package io.wifi.starrailexpress.plush;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.network.PlushEquipmentSyncPayload;
import io.wifi.starrailexpress.sponsor.SponsorManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 身份玩偶管理器（服务端权威）。
 * <p>
 * 扫描每位玩家背包中的 {@code SREPlushItem}（优先赞助者专属玩偶），广播给所有客户端，
 * 使变形后的外观可以像帽子一样绑定到「显示皮肤拥有者」的玩偶，避免用独特玩偶识人。
 */
public final class PlushEquipmentManager {
    public static final String NONE = PlushEquipmentIdentity.NONE;
    private static final Map<UUID, String> LAST_KNOWN = new ConcurrentHashMap<>();
    private static final int SCAN_INTERVAL_TICKS = 100;

    private PlushEquipmentManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendFullSnapshot(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT
                .register((handler, server) -> LAST_KNOWN.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(PlushEquipmentManager::tick);
    }

    public static String getServerPlushIdentity(Player player) {
        PlushEquipmentIdentity identity = findIdentityPlush(player);
        return identity == null ? NONE : identity.encode();
    }

    public static @Nullable PlushEquipmentIdentity findIdentityPlush(Player player) {
        if (player == null) {
            return null;
        }
        PlushEquipmentIdentity first = null;
        // 直接遍历原槽位，避免每次渲染查询都复制背包；找到首个赞助者后后续候选不可能覆盖它。
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            PlushEquipmentIdentity identity = PlushEquipmentIdentity.fromStack(inventory.getItem(i));
            if (identity == null) {
                continue;
            }
            if (isSponsorIdentity(identity)) {
                return identity;
            }
            if (first == null) {
                first = identity;
            }
        }
        ExtraSlotComponent extra = ExtraSlotComponent.KEY.getNullable(player);
        if (extra != null) {
            for (ItemStack stack : extra.SLOTS.values()) {
                PlushEquipmentIdentity identity = PlushEquipmentIdentity.fromStack(stack);
                if (identity == null) {
                    continue;
                }
                if (isSponsorIdentity(identity)) {
                    return identity;
                }
                if (first == null) {
                    first = identity;
                }
            }
        }
        return first;
    }

    private static boolean isSponsorIdentity(PlushEquipmentIdentity identity) {
        if (identity == null) {
            return false;
        }
        String skin = identity.sponsorSkinName();
        if (skin.isEmpty()) {
            return false;
        }
        return SponsorManager.getSponsorPlushNames().contains(skin);
    }


    private static void sendFullSnapshot(ServerPlayer recipient) {
        MinecraftServer server = recipient.getServer();
        if (server == null || !ServerPlayNetworking.canSend(recipient, PlushEquipmentSyncPayload.ID)) {
            return;
        }
        Map<UUID, String> snapshot = null;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String identity = getServerPlushIdentity(online);
            LAST_KNOWN.put(online.getUUID(), identity);
            if (NONE.equals(identity)) {
                continue;
            }
            if (snapshot == null) {
                snapshot = new HashMap<>();
            }
            snapshot.put(online.getUUID(), identity);
        }
        if (snapshot != null && !snapshot.isEmpty()) {
            ServerPlayNetworking.send(recipient, PlushEquipmentSyncPayload.full(snapshot));
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        Map<UUID, String> changes = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String current = getServerPlushIdentity(player);
            String last = LAST_KNOWN.get(uuid);
            if (!current.equals(last == null ? NONE : last)) {
                LAST_KNOWN.put(uuid, current);
                if (changes == null) {
                    changes = new HashMap<>(4);
                }
                changes.put(uuid, current);
            }
        }
        LAST_KNOWN.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
        if (changes == null || changes.isEmpty()) {
            return;
        }
        List<ServerPlayer> recipients = null;
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(recipient, PlushEquipmentSyncPayload.ID)) {
                if (recipients == null) {
                    recipients = new ArrayList<>();
                }
                recipients.add(recipient);
            }
        }
        if (recipients == null) {
            return;
        }
        PlushEquipmentSyncPayload payload = PlushEquipmentSyncPayload.incremental(changes);
        for (ServerPlayer recipient : recipients) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }
}
