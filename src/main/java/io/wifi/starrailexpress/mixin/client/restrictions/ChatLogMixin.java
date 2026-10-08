package io.wifi.starrailexpress.mixin.client.restrictions;

import java.time.Instant;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.client.multiplayer.chat.ChatTrustLevel;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;

@Mixin(ChatListener.class)
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
     * 阻止玩家聊天消息写入 ChatLog（举报界面用的记录）
     * 注意：聊天栏显示逻辑在 showMessageToPlayer 中，不受影响。
     */
    @Inject(method = "logPlayerMessage", at = @At("HEAD"), cancellable = true)
    private void cancelLogPlayerMessage(PlayerChatMessage message,
            ChatType.Bound bound,
            GameProfile sender,
            ChatTrustLevel trustLevel,
            CallbackInfo ci) {
        if (shouldBlockChatMessage())
            ci.cancel();
    }

    /**
     * 阻止系统消息写入 ChatLog
     * （加入/离开/公告等，如果你只想处理玩家聊天，可以删掉这个方法）
     */
    @Inject(method = "logSystemMessage", at = @At("HEAD"), cancellable = true)
    private void cancelLogSystemMessage(Component component, Instant instant, CallbackInfo ci) {
        if (shouldBlockChatMessage()) {
            ci.cancel();
        }
    }
}
