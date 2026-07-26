package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.noellesroles.voice.client.BeepRobotVoiceClientReceiver;

/**
 * 注册 beep_voice / robot_voice 的客户端音频处理。
 * 仅在客户端环境注册（音频处理全在听者客户端完成）。
 */
public class BeepRobotVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "noellesroles_beep_robot_voice";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        BeepRobotVoiceClientReceiver.register(registration);
    }
}
