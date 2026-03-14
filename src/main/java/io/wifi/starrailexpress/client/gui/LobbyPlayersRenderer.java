package io.wifi.starrailexpress.client.gui;

import dev.doctor4t.ratatouille.util.TextUtils;
import io.wifi.starrailexpress.cca.AutoStartComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.MapVotingComponent;
import io.wifi.starrailexpress.client.InputHandler;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class LobbyPlayersRenderer {
    public static void renderHud(Font font, @NotNull LocalPlayer player, @NotNull GuiGraphics guiGraphics) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        if (!game.isRunning()) {
            Level world = player.level();
            List<? extends Player> players = world.players();
            int count = players.size();
            int readyPlayerCount = GameUtils.getReadyPlayerCount(world);
            
            // 绘制玩家计数信息
            drawPlayerCountInfo(guiGraphics, font, readyPlayerCount, count);
            
            // 绘制自动开始信息
            drawAutoStartInfo(guiGraphics, font, world, game);
            
            // 绘制投票信息（如果投票活跃）
            drawVotingInfo(guiGraphics, font, world);
            
            // 绘制感谢文本
            drawCreditsText(guiGraphics, font);
        }
    }
    
    private static void drawPlayerCountInfo(GuiGraphics guiGraphics, Font font, int readyPlayerCount, int totalCount) {
        // 背景矩形
        int bgWidth = 200;
        int bgHeight = 20;
        int x = (guiGraphics.guiWidth() - bgWidth) / 2;
        int y = 5;
        
        // 注释掉背景矩形绘制
        /*
        // 绘制半透明背景
        guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x80000000); // 半透明黑色背景
        
        // 绘制边框
        guiGraphics.fill(x, y, x + bgWidth, y + 1, 0xFF4CC9F0); // 顶边框
        guiGraphics.fill(x, y + bgHeight - 1, x + bgWidth, y + bgHeight, 0xFF4CC9F0); // 底边框
        guiGraphics.fill(x, y, x + 1, y + bgHeight, 0xFF4CC9F0); // 左边框
        guiGraphics.fill(x + bgWidth - 1, y, x + bgWidth, y + bgHeight, 0xFF4CC9F0); // 右边框
        */
        
        // 绘制玩家计数文本
        MutableComponent playerCountText = Component.translatable("lobby.players.count", readyPlayerCount, totalCount);
        int textWidth = font.width(playerCountText);
        int textX = x + (bgWidth - textWidth) / 2;
        int textY = y + (bgHeight - font.lineHeight) / 2;
        
        guiGraphics.drawString(font, playerCountText, textX, textY, 0xFFFFFFFF, false);
    }
    
    private static void drawAutoStartInfo(GuiGraphics guiGraphics, Font font, Level world, SREGameWorldComponent game) {
        AutoStartComponent autoStartComponent = AutoStartComponent.KEY.get(world);
        if (autoStartComponent.isAutoStartActive()) {
            int readyPlayerCount = GameUtils.getReadyPlayerCount(world);
            
            // 计算位置（在玩家计数下方）
            int bgWidth = 200;
            int bgHeight = 15;
            int x = (guiGraphics.guiWidth() - bgWidth) / 2;
            int y = 30; // 在玩家计数框下方
            
            // 注释掉背景矩形绘制
            /*
            // 绘制半透明背景
            guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x80000000);
            
            // 绘制边框
            guiGraphics.fill(x, y, x + bgWidth, y + 1, 0xFF70E000); // 绿色顶边框表示自动开始
            guiGraphics.fill(x, y + bgHeight - 1, x + bgWidth, y + bgHeight, 0xFF70E000);
            guiGraphics.fill(x, y, x + 1, y + bgHeight, 0xFF70E000);
            guiGraphics.fill(x + bgWidth - 1, y, x + bgWidth, y + bgHeight, 0xFF70E000);
            */
            
            MutableComponent autoStartText;
            int color = 0xFFAAAAAA;
            if (readyPlayerCount >= game.getGameMode().minPlayerCount) {
                int seconds = autoStartComponent.getTime() / 20;
                autoStartText = Component.translatable(seconds <= 0 ? "lobby.autostart.starting" : "lobby.autostart.time", seconds);
                color = 0xFF00BC16; // 绿色表示即将开始
            } else {
                autoStartText = Component.translatable("lobby.autostart.active");
            }
            
            int textWidth = font.width(autoStartText);
            int textX = x + (bgWidth - textWidth) / 2;
            int textY = y + (bgHeight - font.lineHeight) / 2;
            
            guiGraphics.drawString(font, autoStartText, textX, textY, color, false);
        }
    }
    
    private static void drawVotingInfo(GuiGraphics guiGraphics, Font font, Level world) {
        final var mapVotingComponent = MapVotingComponent.KEY.get(world);
        if (mapVotingComponent.isVotingActive()) {
            // 投票信息框
            int bgWidth = 300;
            int bgHeight = 40;
            int x = (guiGraphics.guiWidth() - bgWidth) / 2;
            int y = 50; // 在自动开始信息下方
            
            // 注释掉背景矩形绘制
            /*
            // 绘制半透明背景
            guiGraphics.fill(x, y, x + bgWidth, y + bgHeight, 0x90000000);
            
            // 绘制边框
            guiGraphics.fill(x, y, x + bgWidth, y + 2, 0xFFFFA500); // 橙色顶边框表示投票
            guiGraphics.fill(x, y + bgHeight - 2, x + bgWidth, y + bgHeight, 0xFFFFA500);
            guiGraphics.fill(x, y, x + 2, y + bgHeight, 0xFFFFA500);
            guiGraphics.fill(x + bgWidth - 2, y, x + bgWidth, y + bgHeight, 0xFFFFA500);
            */
            
            // 绘制投票标题
            String keyBindName = InputHandler.getOpenVotingScreenKeybind().getTranslatedKeyMessage().getString();
            Component subtitle = Component.translatable("gui.sre.map_selector.subtitle", keyBindName);
            
            int titleWidth = font.width(subtitle);
            int titleX = x + (bgWidth - titleWidth) / 2;
            int titleY = y + 5;
            
            guiGraphics.drawString(font, subtitle, titleX, titleY, 0xFFFFFFFF, false);
            
            // 绘制投票倒计时
            Component timerText = Component.translatable("gui.sre.map_selector.voting_timer", mapVotingComponent.getVotingTimeLeft()/20);
            int timerWidth = font.width(timerText);
            int timerX = x + (bgWidth - timerWidth) / 2;
            int timerY = y + 20;
            
            guiGraphics.drawString(font, timerText, timerX, timerY, 0xFFFFFF00, false); // 黄色倒计时
        }
    }
    
    private static void drawCreditsText(GuiGraphics guiGraphics, Font font) {
        float scale = 0.75f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, guiGraphics.guiHeight(), 0);
        guiGraphics.pose().scale(scale, scale, 1f);
        
        int i = 0;
        MutableComponent thanksText = Component.translatable("credits.starrailexpress.thank_you");
        String fallback = "Thank you for playing The Last Voyage of the Harpy Express!\nMe and my team spent a lot of time working\non this mod and we hope you enjoy it.\nIf you do and wish to make a video or stream\nplease make sure to credit my channel,\nvideo and the mod page!\n - RAT / doctor4t";
        
        if (!thanksText.getString().contains(" - RAT / doctor4t")) {
            thanksText = Component.literal(fallback);
        }

        for (Component text : TextUtils.getWithLineBreaks(thanksText)) {
            i++;
            guiGraphics.drawString(font, text, 10, -90 + 10 * i, 0x80FFFFFF); // 使用半透明白色
        }
        
        guiGraphics.pose().popPose();
    }
}