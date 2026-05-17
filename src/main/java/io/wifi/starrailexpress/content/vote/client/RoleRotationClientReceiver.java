package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.client.gui.screen.gamemode.role_rotation.RoleRotationScreen;
import io.wifi.starrailexpress.network.RoleRotationSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

public class RoleRotationClientReceiver {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(RoleRotationSyncS2CPacket.TYPE, (payload, context) -> {
            Minecraft mc = context.client();
            mc.execute(() -> {
                // 保存上次的轮到状态
                boolean previousWasMyTurn = RoleRotationCache.getWasMyTurn();

                // 更新客户端缓存
                RoleRotationCache.updateFromPacket(payload);

                // 检测是否轮到自己（从不是轮到变为轮到）- 仅客户端播放村民音效
                boolean currentIsMyTurn = RoleRotationCache.getWasMyTurn();
                if (!previousWasMyTurn && currentIsMyTurn && mc.player != null) {
                    mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.VILLAGER_YES, 1.0f, 1.0f)
                    );
                }

                // 如果isSelecting变为false，关闭界面
                if (!payload.isSelecting() && payload.getConfirmCountdown() <= 0) {
                    if (mc.screen instanceof RoleRotationScreen) {
                        mc.setScreen(null);
                    }
                }

                // 如果当前在轮选界面，更新界面
                if (mc.screen instanceof RoleRotationScreen screen) {
                    screen.updateData();
                }
            });
        });
    }
}
