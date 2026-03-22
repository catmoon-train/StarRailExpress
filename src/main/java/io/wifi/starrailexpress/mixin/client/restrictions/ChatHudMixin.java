package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatComponent.class)
public class ChatHudMixin {
    @WrapMethod(method = "render")
    public void tmm$disableChatRender(GuiGraphics context, int currentTick, int mouseX, int mouseY, boolean focused,
            Operation<Void> original) {
        final var minecraft = Minecraft.getInstance();

        // 如果玩家不存在，直接渲染聊天框
        if (minecraft.player == null || SREClient.isInLobby) {
            original.call(context, currentTick, mouseX, mouseY, focused);
            return;
        }
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SRE.cantUseChatHud.stream().anyMatch((pre) -> pre.test(minecraft.player))) {
            return;
        }
        // 如果游戏组件不存在或玩家不在游戏中，直接渲染聊天框
        if (SREClient.gameComponent == null || !SREClient.isPlayerAliveAndInSurvival()) {
            original.call(context, currentTick, mouseX, mouseY, focused);
            return;
        }
        // 获取玩家角色
        final var playerRole = SREClient.gameComponent.getRole(minecraft.player);

        // 如果玩家有角色且该角色允许使用聊天框，或者游戏未运行，则渲染聊天框
        if (SRE.canUseChatHudPlayer.stream().anyMatch(predicate -> predicate.test(minecraft.player))
                || (playerRole != null && SRE.canUseChatHud.stream().anyMatch(predicate -> predicate.test(playerRole)))
                || !SREClient.gameComponent.isRunning()) {
            original.call(context, currentTick, mouseX, mouseY, focused);
        }
    }
}