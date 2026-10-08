package io.wifi.starrailexpress.mixin.client.restrictions;

import java.time.Instant;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.client.multiplayer.chat.LoggedChatEvent;
import net.minecraft.client.multiplayer.chat.LoggedChatMessage;
import net.minecraft.network.chat.Component;

@Mixin(ChatLog.class)
public class ChatLogReportMixin {
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
        if (SREClient.isGameRunning()) {
            return true;
        }
        return false;
    }

    /**
     * 阻止玩家获取举报信息
     */
    @Inject(method = "lookup", at = @At("HEAD"), cancellable = true)
    private void logChatMessage(int i, CallbackInfoReturnable<LoggedChatEvent> cir) {
        if (shouldBlockChatMessage()) {
            cir.setReturnValue(
                    LoggedChatMessage.system(Component.translatable("sre.chat_report.message.blocked"), Instant.now()));
        }
    }
}
