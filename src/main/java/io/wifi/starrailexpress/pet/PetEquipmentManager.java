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

package io.wifi.starrailexpress.pet;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.network.PetSyncPayload;
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
 * 随从装备管理器（服务端权威）。
 * <p>
 * 随从本身是<b>纯客户端虚拟对象</b>：服务端不生成任何实体、不 tick 任何东西，
 * 只负责把「谁装备了哪个随从、是否愿意公开」同步给客户端。
 * 位置由客户端依据已同步的玩家坐标自行推导，因此逐帧零发包。
 * <p>
 * 装备信息复用皮肤系统：随从皮肤存于 {@link SREPlayerSkinsComponent} 的
 * equippedSkins 中，类型为 {@code "pet"}；「展示给其他玩家」是同一组件上的
 * 一个布尔量。由于皮肤组件只同步给玩家本人，本管理器负责：
 * <ul>
 * <li>玩家加入时向新玩家发送全量随从快照；</li>
 * <li>周期性扫描在线玩家的随从变化并向所有客户端增量广播；</li>
 * <li>玩家退出时清理记录。</li>
 * </ul>
 * 客户端缓存见 {@code io.wifi.starrailexpress.client.pet.ClientPetCache}。
 */
public final class PetEquipmentManager {
    /** 随从在皮肤系统中的类型名 */
    public static final String PET_TYPE = ItemSkinManager.SkinTypes.PET;
    /** 未装备随从时的皮肤名 */
    public static final String NO_PET = "default";

    /** 服务端最近一次已广播的随从状态 */
    private static final Map<UUID, PetSyncPayload.Entry> LAST_KNOWN = new ConcurrentHashMap<>();

    /** 扫描间隔（tick）：100 tick = 5 秒 */
    private static final int SCAN_INTERVAL_TICKS = 100;

    private PetEquipmentManager() {
    }

    public static void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendFullSnapshot(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT
                .register((handler, server) -> LAST_KNOWN.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(PetEquipmentManager::tick);
    }

    /**
     * 获取服务端权威的某玩家随从状态（未装备时 {@link PetSyncPayload.Entry#hasPet()} 为 false）。
     */
    public static PetSyncPayload.Entry getServerPetState(Player player) {
        if (player == null || !SREConfig.instance().isItemSkinEnabled) {
            return PetSyncPayload.Entry.none();
        }
        SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY.get(player);
        if (component == null) {
            return PetSyncPayload.Entry.none();
        }
        String skin = component.getEquippedSkin(PET_TYPE);
        if (skin == null || skin.isBlank() || NO_PET.equals(skin)) {
            return PetSyncPayload.Entry.none();
        }
        return new PetSyncPayload.Entry(skin, component.isPetPublic());
    }

    /**
     * 向指定玩家发送当前所有在线玩家的随从全量快照。
     * 只包含实际装备了随从的玩家——未装备即默认状态，客户端清空后即为默认，
     * 因此无人装备随从时不发包。
     */
    private static void sendFullSnapshot(ServerPlayer recipient) {
        MinecraftServer server = recipient.getServer();
        if (server == null || !ServerPlayNetworking.canSend(recipient, PetSyncPayload.ID)) {
            return;
        }
        Map<UUID, PetSyncPayload.Entry> snapshot = null;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            PetSyncPayload.Entry entry = getServerPetState(online);
            if (!entry.hasPet()) {
                continue;
            }
            if (snapshot == null) {
                snapshot = new HashMap<>();
            }
            snapshot.put(online.getUUID(), entry);
        }
        if (snapshot != null && !snapshot.isEmpty()) {
            ServerPlayNetworking.send(recipient, PetSyncPayload.full(snapshot));
        }
    }

    /**
     * 每 {@value #SCAN_INTERVAL_TICKS} tick（5 秒）扫描一次在线玩家的随从变化。
     * 仅在发生变化时才组包，且只发给能接收的客户端；无变化零发包。
     */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        Map<UUID, PetSyncPayload.Entry> changes = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            PetSyncPayload.Entry current = getServerPetState(player);
            PetSyncPayload.Entry last = LAST_KNOWN.get(uuid);
            boolean same = last != null
                    && last.shared() == current.shared()
                    && (last.skinId() == null ? "" : last.skinId())
                            .equals(current.skinId() == null ? "" : current.skinId());
            if (!same) {
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
            if (ServerPlayNetworking.canSend(recipient, PetSyncPayload.ID)) {
                if (recipients == null) {
                    recipients = new ArrayList<>();
                }
                recipients.add(recipient);
            }
        }
        if (recipients == null) {
            return;
        }
        PetSyncPayload payload = PetSyncPayload.incremental(changes);
        for (ServerPlayer recipient : recipients) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }
}
