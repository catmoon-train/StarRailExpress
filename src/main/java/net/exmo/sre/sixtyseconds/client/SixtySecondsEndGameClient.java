package net.exmo.sre.sixtyseconds.client;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.network.SixtySecondsEndGamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端接收 60s 结算数据并打开 {@link SixtySecondsEndScreen}。
 */
public final class SixtySecondsEndGameClient {

    /** 最近一次收到的结算数据（仅在屏幕打开期间有效）。 */
    private static volatile SixtySecondsEndGamePayload cachedData;

    private SixtySecondsEndGameClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SixtySecondsEndGamePayload.ID, (payload, context) -> {
            cachedData = payload;
            context.client().execute(() -> {
                var client = context.client();
                if (client.screen instanceof SixtySecondsEndScreen) return; // 已打开则忽略重复
                client.setScreen(new SixtySecondsEndScreen(payload));
            });
        });
    }

    /** 如果客户端缓存了结算数据则返回，否则返回 null。 */
    public static SixtySecondsEndGamePayload getCached() {
        return cachedData;
    }
}
