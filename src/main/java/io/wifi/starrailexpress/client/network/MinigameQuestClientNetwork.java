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
