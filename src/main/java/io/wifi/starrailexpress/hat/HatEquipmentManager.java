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

package io.wifi.starrailexpress.hat;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.network.HatEquipmentSyncPayload;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 帽子装备管理器（服务端权威）。
 * <p>
 * 玩家装备的帽子本质上是皮肤系统中类型为 {@code "hat"} 的装备皮肤，
 * 存储于 {@link SREPlayerSkinsComponent} 的 equippedSkins 中。
 * 由于皮肤组件只同步给玩家本人，其他客户端无法得知某玩家装备了什么帽子，
 * 因此本管理器负责：
 * <ul>
 * <li>玩家加入时向新玩家发送全量帽子装备快照；</li>
 * <li>周期性扫描在线玩家的帽子装备变化并向所有客户端增量广播；</li>
 * <li>玩家退出时清理记录。</li>
 * </ul>
 * 客户端缓存见 {@code io.wifi.starrailexpress.client.hat.ClientHatEquipmentCache}。
 */
public final class HatEquipmentManager {
    /** 帽子在皮肤系统中的类型名 */
    public static final String HAT_TYPE = ItemSkinManager.SkinTypes.HAT;
    /** 未装备帽子时的皮肤名 */
    public static final String NO_HAT = "default";

    /** 服务端最近一次已广播的帽子装备状态 */
    private static final Map<UUID, String> LAST_KNOWN = new ConcurrentHashMap<>();

    /** 扫描间隔（tick）：100 tick = 5 秒 */
    private static final int SCAN_INTERVAL_TICKS = 100;

    private HatEquipmentManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendFullSnapshot(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_KNOWN.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(HatEquipmentManager::tick);
    }

    /**
     * 获取服务端权威的某玩家帽子皮肤名（未装备返回 {@link #NO_HAT}）。
     */
    public static String getServerHatSkinName(Player player) {
        if (player == null || !SREConfig.instance().isItemSkinEnabled) {
            return NO_HAT;
        }
        SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
        if (component == null) {
            return NO_HAT;
        }
        String skin = component.getEquippedSkin(HAT_TYPE);
        return skin == null || skin.isBlank() ? NO_HAT : skin;
    }

    /**
     * 向指定玩家发送当前所有在线玩家的帽子装备全量快照。
     * 只包含实际装备了帽子的玩家（未装备为默认状态，客户端清空后即为默认），
     * 减小包体；无人装备帽子时不发包。
     */
    private static void sendFullSnapshot(ServerPlayer recipient) {
        MinecraftServer server = recipient.getServer();
        if (server == null || !ServerPlayNetworking.canSend(recipient, HatEquipmentSyncPayload.ID)) {
            return;
        }
        Map<UUID, String> snapshot = null;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            String hat = getServerHatSkinName(online);
            if (NO_HAT.equals(hat)) {
                continue;
            }
            if (snapshot == null) {
                snapshot = new HashMap<>();
            }
            snapshot.put(online.getUUID(), hat);
        }
        // 自己也装备着帽子时才需要同步；无人戴帽子时客户端天然为空缓存，无需发包
        if (snapshot != null && !snapshot.isEmpty()) {
            ServerPlayNetworking.send(recipient, HatEquipmentSyncPayload.full(snapshot));
        }
    }

    /**
     * 每 {@value #SCAN_INTERVAL_TICKS} tick（5 秒）扫描一次在线玩家的帽子装备变化。
     * 仅在发生变化时才组包，且只发给能接收的客户端；无变化零发包。
     */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        Map<UUID, String> changes = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            String current = getServerHatSkinName(player);
            String last = LAST_KNOWN.get(uuid);
            if (!current.equals(last == null ? NO_HAT : last)) {
                LAST_KNOWN.put(uuid, current);
                if (changes == null) {
                    changes = new HashMap<>(4);
                }
                changes.put(uuid, current);
            }
        }
        // 清理已离线玩家的记录（双保险，DISCONNECT 事件已处理）
        LAST_KNOWN.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);

        if (changes == null || changes.isEmpty()) {
            return;
        }
        // 先筛出能接收的客户端，没有可接收者则连包都不用组
        List<ServerPlayer> recipients = null;
        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(recipient, HatEquipmentSyncPayload.ID)) {
                if (recipients == null) {
                    recipients = new ArrayList<>();
                }
                recipients.add(recipient);
            }
        }
        if (recipients == null) {
            return;
        }
        HatEquipmentSyncPayload payload = HatEquipmentSyncPayload.incremental(changes);
        for (ServerPlayer recipient : recipients) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }
}
