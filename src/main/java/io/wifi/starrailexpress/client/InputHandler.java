package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.client.gui.screen.MapSelectorScreen;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class InputHandler {
    private static KeyMapping openVotingScreenKeybind;

    public static void initialize() {
        openVotingScreenKeybind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.starrailexpress.open_voting_screen",
                GLFW.GLFW_KEY_M,
                "category.starrailexpress.general"));

        ClientTickEvents.END_CLIENT_TICK.register(InputHandler::onClientTick);
    }

    public static KeyMapping getOpenVotingScreenKeybind() {
        return openVotingScreenKeybind;
    }

    private static void onClientTick(Minecraft client) {
        if (client == null)
            return;
        if (client.level == null)
            return;

        // 检查玩家是否持有狙击枪，如果不持有则关闭瞄准镜
        if (ScopeOverlayRenderer.isInScopeView() && client.player != null) {
            ItemStack mainHandItem = client.player.getMainHandItem();
            if (!mainHandItem.is(TMMItems.SNIPER_RIFLE)) {
                ScopeOverlayRenderer.setInScopeView(false);
            }
        }

        if (openVotingScreenKeybind.consumeClick()) {
            // 检查是否处于投票阶段
            final MapVotingComponent mapVotingComponent = MapVotingComponent.KEY.get(client.level);
            if (mapVotingComponent.isVotingActive()) {
                // 打开投票界面
                client.setScreen(new MapSelectorScreen());
            }
        }
    }
}