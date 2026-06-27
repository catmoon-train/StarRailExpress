package org.agmas.noellesroles.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import net.minecraft.util.Mth;
import org.agmas.noellesroles.packet.AmonFinaleS2CPacket;
import org.agmas.noellesroles.packet.AmonSkinS2CPacket;

import io.wifi.starrailexpress.client.StatusBarHUD;
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

    /** 终幕「阿蒙时刻」全局表现：偏灰滤镜、小丑音乐与状态栏倒计时是否激活。 */
    public static volatile boolean finaleActive = false;
    private static long finaleStartMs = 0L;
    /** 终幕总时长（毫秒），与服务端 FINALE_TICKS(80 秒) 对应。 */
    private static final long FINALE_DURATION_MS = 80_000L;

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
        ClientPlayNetworking.registerGlobalReceiver(AmonFinaleS2CPacket.ID,
                (payload, ctx) -> ctx.client().execute(() -> {
                    finaleActive = payload.active();
                    finaleStartMs = System.currentTimeMillis();
                    // 终幕结束：仅把进度归零不会移除状态条（HUD 默认保留 500s），需显式移除。
                    if (!payload.active()) {
                        StatusBarHUD.getInstance().removeStatusBar("AmonFinale");
                    }
                }));
    }

    /** 终幕进度（1→0），供全局状态栏显示倒计时。 */
    public static float finaleProgress() {
        if (!finaleActive) return 0f;
        long elapsed = System.currentTimeMillis() - finaleStartMs;
        return Mth.clamp(1f - (float) elapsed / FINALE_DURATION_MS, 0f, 1f);
    }

    public static UUID disguiseTargetFor(UUID amonId) {
        return amonId == null ? null : skins.get(amonId);
    }

    /** 游戏重置时清空所有阿蒙伪装映射与终幕状态。 */
    public static void clearAll() {
        skins.clear();
        finaleActive = false;
        StatusBarHUD.getInstance().removeStatusBar("AmonFinale");
    }
}
