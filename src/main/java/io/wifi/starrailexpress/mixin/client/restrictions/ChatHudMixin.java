package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
        if (minecraft.player == null || SREClient.shouldRenderVanillaHud()) {
            original.call(context, currentTick, mouseX, mouseY, focused);
            return;
        }
        if (SREClient.canRenderChatHud()) {
            original.call(context, currentTick, mouseX, mouseY, focused);
        }
    }
}
