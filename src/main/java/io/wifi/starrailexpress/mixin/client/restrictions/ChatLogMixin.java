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
     * 拦截玩家聊天消息（带签名验证的正式聊天）
     * 在消息进入延迟队列之前记录，确保每条消息只被记录一次。
     */
    @Unique
    private static boolean shouldBlockChatMessage() {
        if (SREClient.cached_player == null)
            return false;
        if (SREClient.isInLobby) {
            return false;
        }
        // if(SREClient.cached_player==null) return false;

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
        if(shouldBlockChatMessage()){
            ci.cancel();
        }
    }
}
