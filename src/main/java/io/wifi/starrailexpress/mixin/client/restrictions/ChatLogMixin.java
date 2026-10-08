package io.wifi.starrailexpress.mixin.client.restrictions;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;

@Mixin(ChatComponent.class)
public class ChatLogMixin {
    /**
     * 是否应当阻止保存聊天信息到日志（防止玩家偷窥日志看消息）
     * 仅拦截客户端，因为服务端没啥必要（（（）））
     * 大厅不拦截
     * 管理员不用拦截：没必要
     * 游戏不启动不用拦截：拦截寂寞
     * 死亡惩罚要拦截
     * 活着要拦截
     */
    @Unique
    private static boolean shouldBlockChatMessage() {
        if (SREClient.cached_player == null)
            return false;
        if (SREClient.isInLobby) {
            return false;
        }
        if (!SREClient.isGameRunning()) {
            return false;
        }
        if (SREClient.cached_player.hasPermissions(1)){
            return false;
        }
        if (SREClient.hasPenalty()) {
            return true;
        }
        if (SREClient.isPlayerAliveAndInSurvival()) {
            return true;
        }
        return false;
    }

    /**
     * 阻止玩家聊天消息写入日志
     * 注意：聊天栏显示逻辑在 showMessageToPlayer 中，不受影响。
     */
    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void logChatMessage(GuiMessage guiMessage, CallbackInfo ci) {
        if (shouldBlockChatMessage()) {
            ci.cancel();
        }
    }
}
