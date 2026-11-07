package io.wifi.starrailexpress.mixin.compat.voicechat;

import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.intercompatibility.ClientCompatibilityManager;
import de.maxhenkel.voicechat.net.ClientServerNetManager;
import de.maxhenkel.voicechat.net.RequestSecretPacket;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import io.wifi.srepackcompat.voice.VoicechatReconnect;
import io.wifi.srepackcompat.voice.VoicechatReconnectBackoff;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只恢复本会话曾成功建立的语音连接，重新获取密钥并沿用上游认证流程。 */
@Pseudo
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.ClientManager", remap = false)
public abstract class VoicechatClientManagerMixin implements VoicechatReconnect {
    @Shadow private ClientVoicechat client;
    @Unique private final VoicechatReconnectBackoff sreCompat$backoff = new VoicechatReconnectBackoff();

    @Inject(method = {"onJoinWorld", "onDisconnect"}, at = @At("HEAD"))
    private void sreCompat$clearSession(CallbackInfo ci) {
        sreCompat$backoff.reset();
    }

    @Override
    public void sreCompat$tickReconnect() {
        Minecraft minecraft = Minecraft.getInstance();
        if (client == null || minecraft.getConnection() == null || minecraft.level == null) {
            sreCompat$backoff.reset();
            return;
        }
        var connection = client.getConnection();
        // isConnected 仅代表 UDP socket 打开，必须等 isInitialized 确认握手完成。
        if (connection != null && connection.isInitialized() && connection.isConnected()) {
            sreCompat$backoff.connected();
            return;
        }
        ClientPlayerStateManager state = ClientManager.getPlayerStateManager();
        if (state.isDisabled()) {
            sreCompat$backoff.pause();
            return;
        }
        if (!sreCompat$backoff.shouldRetry(System.nanoTime())) return;
        try {
            // 终止旧认证/麦克风线程，防止每档重试留下并行 UDP 连接。
            if (connection != null) {
                ClientCompatibilityManager.INSTANCE.emitVoiceChatDisconnectedEvent();
            }
            Voicechat.LOGGER.info("[SRE Compat] Retrying lost voice chat connection", new Object[0]);
            ClientServerNetManager.sendToServer(new RequestSecretPacket(Voicechat.COMPATIBILITY_VERSION));
        } catch (Exception error) {
            Voicechat.LOGGER.warn("[SRE Compat] Voice chat reconnect failed", error);
        }
    }
}
