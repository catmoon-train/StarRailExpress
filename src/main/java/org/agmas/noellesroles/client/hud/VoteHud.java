package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.client.InputHandler;
import io.wifi.starrailexpress.content.vote.client.ClientVoteCache;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

public class VoteHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((guiGraphics, deltaTracker)->{
            // 投票信息框
            if(!ClientVoteCache.isActive()){
                return;
            }
            int bgWidth = 300;
            int x = (guiGraphics.guiWidth() - bgWidth) / 2;
            int y = 50; // 在自动开始信息下方

            // 注释掉背景矩形绘制
            /*
             * // 绘制半透明背景
             * guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x90000000);
             * 
             * // 绘制边框
             * guiGraphics.fill(x, y, x + bgWidth, y + 2, 0xFFFFA500); // 橙色顶边框表示投票
             * guiGraphics.fill(x, y + bgHeight - 2, x + bgWidth, y + bgHeight, 0xFFFFA500);
             * guiGraphics.fill(x, y, x + 2, y + bgHeight, 0xFFFFA500);
             * guiGraphics.fill(x + bgWidth - 2, y, x + bgWidth, y + bgHeight, 0xFFFFA500);
             */
            final var client = Minecraft.getInstance();
            final var font = client.font;
            // 绘制投票标题
            String keyBindName = InputHandler.getOpenVotingScreenKeybind().getTranslatedKeyMessage().getString();
            Component subtitle = Component.translatable("gui.sre.vote.subtitle", keyBindName);

            int titleWidth = font.width(subtitle);
            int titleX = x + (bgWidth - titleWidth) / 2;
            int titleY = y + 5;

            guiGraphics.drawString(font, subtitle, titleX, titleY, 0xFFFFFFFF, false);
            int remaing = ClientVoteCache.getRemainingSeconds();
            // 绘制投票倒计时
            Component timerText = Component.translatable("gui.sre.vote.remaing_time",
                    remaing);
            int timerWidth = font.width(timerText);
            int timerX = x + (bgWidth - timerWidth) / 2;
            int timerY = y + 20;

            guiGraphics.drawString(font, timerText, timerX, timerY, 0xFFFFFF00, false); // 黄色倒计时

            // 绘制预设游戏模式信息
            
            Component presetText = Component.translatable("gui.sre.vote.title", ClientVoteCache.getTitle());
            int presetWidth = font.width(presetText);
            int presetX = x + (bgWidth - presetWidth) / 2;
            int presetY = y + 35;

            guiGraphics.drawString(font, presetText, presetX, presetY, 0xFFFFA500, false); // 橙色预设模式
        });
    }

}
