package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import org.agmas.noellesroles.packet.AmonSkinS2CPacket;

import io.wifi.starrailexpress.client.util.ClientSkinCache;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阿蒙夺舍后的客户端皮肤顶替状态：对所有玩家可见，将阿蒙渲染为被夺舍宿主的完整皮肤。
 */
public class ClientAmonState {
    private static final Map<UUID, UUID> skins = new ConcurrentHashMap<>();

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player) -> {
            UUID targetId = disguiseTargetFor(player.getUUID());
            if (targetId == null || targetId.equals(player.getUUID())) {
                return PlayerSkinResult.SKIP;
            }
            PlayerInfo info = ClientSkinCache.getCachedPlayerInfo(targetId);
            Minecraft client = Minecraft.getInstance();
            if (info == null && client.getConnection() != null) {
                info = client.getConnection().getPlayerInfo(targetId);
            }
            if (info != null && info.getSkin() != null) {
                return PlayerSkinResult.playerSkin(info.getSkin());
            }
            return PlayerSkinResult.SKIP;
        });
        ClientPlayNetworking.registerGlobalReceiver(AmonSkinS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    if (payload.amonId() == null) {
                        skins.clear();
                    } else if (payload.hostId() != null) {
                        skins.put(payload.amonId(), payload.hostId());
                    } else {
                        skins.remove(payload.amonId());
                    }
                }));
    }

    public static UUID disguiseTargetFor(UUID amonId) {
        return amonId == null ? null : skins.get(amonId);
    }

    /** 游戏重置时清空所有阿蒙伪装映射。 */
    public static void clearAll() {
        skins.clear();
    }
}
