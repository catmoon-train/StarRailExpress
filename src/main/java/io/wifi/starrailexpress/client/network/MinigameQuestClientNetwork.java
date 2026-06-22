package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.MinigameQuestConfigScreen;
import io.wifi.starrailexpress.client.gui.screen.MinigameScreenFactory;
import io.wifi.starrailexpress.network.MinigameQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * 小游戏任务点方块 — 客户端网络处理
 */
public class MinigameQuestClientNetwork {

    public static void register() {
        // 创造模式：打开配置界面
        ClientPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.OpenConfig.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> client.setScreen(new MinigameQuestConfigScreen(
                            payload.pos(),
                            payload.data().getString("MinigameId"),
                            payload.data().getInt("MarkerColor"),
                            payload.data().getBoolean("IsTaskMarker"),
                            payload.data().getBoolean("IsSabotageTrigger"),
                            payload.data().getInt("SabotageDuration"),
                            payload.data().getInt("SabotageCooldown"))));
                });

        // 冒险模式：打开小游戏界面
        ClientPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.OpenGame.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        // onSuccess → 发送完成通知到服务端
                        Runnable onSuccess = () -> ClientPlayNetworking.send(
                                new MinigameQuestPayload.CompleteGame(payload.pos()));
                        Screen screen = MinigameScreenFactory.create(
                                payload.minigameId(), payload.pos(), onSuccess);
                        if (screen != null) {
                            client.setScreen(screen);
                        }
                    });
                });
    }
}
