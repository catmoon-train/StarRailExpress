package io.wifi.srepackcompat.voice;

import de.maxhenkel.voicechat.voice.client.ClientManager;

/** 仅在安装语音模组时注册调用，避免缺少可选目标时加载其类。 */
public interface VoicechatReconnect {
    void sreCompat$tickReconnect();

    static void tick() {
        if (ClientManager.getClient() != null) {
            ((VoicechatReconnect) ClientManager.instance()).sreCompat$tickReconnect();
        }
    }
}
