package io.wifi.starrailexpress.client.util;

import io.sre.client.events.ClientPlayerInfoUpdatePacketEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClientSkinCache {
    public static final Map<UUID, PlayerInfo> PLAYER_ENTRIES_CACHE = new HashMap<>();

    public static PlayerInfo getCachedPlayerInfo(UUID uid) {
        if (uid == null)
            return null;
        PlayerInfo pf = PLAYER_ENTRIES_CACHE.getOrDefault(uid, null);
        return pf;
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((a, b, c) -> {
            // 加入游戏清空信息
            ClientSkinCache.PLAYER_ENTRIES_CACHE.clear();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((a, b) -> {
            // 加入游戏清空信息
            ClientSkinCache.PLAYER_ENTRIES_CACHE.clear();
        });
        // 监听所有接收到的数据包
        ClientPlayerInfoUpdatePacketEvents.UPDATE.register((action, playerinfo) -> {
            if (action.contains(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER)) {
                if (playerinfo.getProfile() != null && playerinfo.getSkin() != null) {
                    var id = playerinfo.getProfile().getId();
                    if (Minecraft.getInstance().player == null || id == Minecraft.getInstance().player.getUUID())
                        return;
                    PLAYER_ENTRIES_CACHE.put(id,
                            playerinfo);
                }
            }
        });
    }

}
