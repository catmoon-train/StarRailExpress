/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.TextBatchingBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

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

    /**
     * 聊天文字批量缓冲在帧末（GUI modelview 弹出前）统一绘制，会显示在所有
     * 屏幕之上。当打开的是其它 screen（非 ChatScreen）时，聊天应该被屏幕盖住，
     * 因此回退到原版路径（画进 GUI 缓冲，随 HUD 一起被屏幕覆盖）。
     */
    @Unique
    private static boolean sre$shouldBatchChat() {
        if (!SREClientConfig.instance().enhancedChatHud) {
            return false;
        }
        Screen screen = Minecraft.getInstance().screen;
        return screen == null || screen instanceof ChatScreen;
    }

    /**
     * Routes chat text glyphs into the shared {@link TextBatchingBuffer#CHAT}
     * buffer instead of the per-glyph GUI BufferSource. The buffer is flushed at
     * the end of the GUI render (GameRendererMixin), keeping the text on top of
     * the chat backdrop while eliminating per-glyph getBuffer lookups.
     */
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int sre$batchChatLine(GuiGraphics graphics, Font font, FormattedCharSequence seq, int x, int y, int color, Operation<Integer> original) {
        if (!sre$shouldBatchChat()) {
            return original.call(graphics, font, seq, x, y, color);
        }
        return font.drawInBatch(seq, x, y, color, false, graphics.pose().last().pose(),
                TextBatchingBuffer.CHAT, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"))
    private int sre$batchChatComponent(GuiGraphics graphics, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
        if (!sre$shouldBatchChat()) {
            return original.call(graphics, font, text, x, y, color);
        }
        return font.drawInBatch(text, x, y, color, false, graphics.pose().last().pose(),
                TextBatchingBuffer.CHAT, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
