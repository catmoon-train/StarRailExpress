package io.wifi.srepackcompat.jei;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** 仅检查当前客户端世界，不改写 JEI 设置或动态注册按键。 */
public final class JeiLobbyGate {
    public static boolean enabled() {
        Minecraft client = Minecraft.getInstance();
        return JeiLobbyPolicy.allows(client.level != null && client.player != null && client.getConnection() != null,
                JeiLobbyPolicy.lobby());
    }
    public static void initialize() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!enabled() && client.screen != null && JeiLobbyPolicy.isPresentation(client.screen.getClass())) {
                // 让 JEI 按正常路径恢复父界面与输入焦点；新界面仍由 setScreen 补丁校验。
                client.screen.onClose();
                if (client.screen != null && JeiLobbyPolicy.isPresentation(client.screen.getClass())) client.setScreen(null);
            }
        });
    }
    private JeiLobbyGate() { }
}
