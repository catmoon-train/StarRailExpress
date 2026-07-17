package io.wifi.starrailexpress.client.util;

import io.sre.client.events.ClientPlayerInfoUpdatePacketEvents;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.mixin.SkinManagerAccessor;

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
        ClientPlayConnectionEvents.DISCONNECT.register((a, client) -> {
            // 加入游戏清空信息
            ClientSkinCache.PLAYER_ENTRIES_CACHE.clear();

            // 清除皮肤缓存
            if (client.player == null)
                return;

            UUID localUuid = client.player.getUUID();
            SkinManager skinManager = client.getSkinManager();
            SkinManagerAccessor accessor = (SkinManagerAccessor) skinManager;
            var cache = accessor.getSkinCache();

            // 复制键集，避免在迭代过程中修改集合导致并发异常
            ArrayList<SkinManager.CacheKey> allKeys = new ArrayList<>(cache.asMap().keySet());

            for (SkinManager.CacheKey key : allKeys) {
                // 仅清除其他玩家的缓存，保留自己的
                if (!key.profileId().equals(localUuid)) {
                    cache.invalidate(key);
                }
            }

            // 立即执行清理，确保旧数据被丢弃
            cache.cleanUp();
            SRE.LOGGER.info("Skin caches cleared on disconnect.");
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
