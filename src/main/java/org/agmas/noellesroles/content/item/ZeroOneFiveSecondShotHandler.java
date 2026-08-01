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

package org.agmas.noellesroles.content.item;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端处理零一五第二枪计时器
 */
@Environment(EnvType.CLIENT)
public class ZeroOneFiveSecondShotHandler implements ClientPlayNetworking.PlayPayloadHandler<ZeroOneFiveSecondShotPayload> {

    /** 延迟时间（tick） = 0.3秒 */
    private static final int DELAY_TICKS = 6;

    @Override
    public void receive(ZeroOneFiveSecondShotPayload payload, ClientPlayNetworking.Context context) {
        Minecraft mc = context.client();
        int shooterId = payload.shooterId();
        
        // 调度延迟执行
        mc.execute(() -> {
            LocalPlayer player = mc.player;
            if (player == null) return;
            
            // 延迟2秒后执行
            new Thread(() -> {
                try {
                    Thread.sleep(DELAY_TICKS * 50L); // 50ms per tick
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                
                // 在主线程执行
                mc.execute(() -> {
                    LocalPlayer localPlayer = mc.player;
                    if (localPlayer != null && localPlayer.getId() == shooterId) {
                        // 模拟右键开枪，发送 isAutoSecondShot=true 的数据包
                        ClientPlayNetworking.send(new ZeroOneFiveShootPayload(-1, true));
                    }
                });
            }).start();
        });
    }
}
