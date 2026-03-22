package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.block.SecurityMonitorBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

public class SecurityCameraHUD {
    // private static final ResourceLocation SECURITY_MONITOR_TEXTURE =
    // ResourceLocation.fromNamespaceAndPath("starrailexpress",
    // "textures/gui/security_monitor.png");
    private static final int HUD_WIDTH = 160;
    private static final int HUD_HEIGHT = 40;
    private static long lastBlinkSwitchTime = System.currentTimeMillis();
    private static boolean shouldBlink = false;

    public static void render(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!SecurityMonitorBlock.isInSecurityMode() || !SREClientConfig.instance().enableSecurityCameraHUD) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }

        PoseStack poseStack = guiGraphics.pose();

        // 更新闪烁效果
        updateBlinkEffect();

        // 渲染监控HUD背景
        // renderSecurityMonitorHUD(guiGraphics, screenWidth, screenHeight);

        // 渲染摄像头信息
        renderCameraInfo(guiGraphics, screenWidth, screenHeight);

        // 渲染退出提示
        renderExitHint(guiGraphics, screenWidth, screenHeight);

        // 渲染状态指示器
        renderStatusIndicator(guiGraphics, screenWidth, screenHeight);
    }

    // private static void renderSecurityMonitorHUD(GuiGraphics guiGraphics, int
    // screenWidth, int screenHeight) {
    // RenderSystem.setShader(GameRenderer::getPositionTexShader);
    // RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    // RenderSystem.setShaderTexture(0, SECURITY_MONITOR_TEXTURE);
    //
    // int x = (screenWidth - HUD_WIDTH) / 2;
    // int y = 10;
    //
    // // 绘制监控HUD边框 - 如果在闪烁状态则改变颜色
    // int borderColor = shouldBlink ? 0xFFFF0000 : 0xFF00FF00;
    // guiGraphics.fill(x, y, x + HUD_WIDTH, y + 2, borderColor); // 顶边框
    // guiGraphics.fill(x, y, x + 2, y + HUD_HEIGHT, borderColor); // 左边框
    // guiGraphics.fill(x, y + HUD_HEIGHT - 2, x + HUD_WIDTH, y + HUD_HEIGHT,
    // borderColor); // 底边框
    // guiGraphics.fill(x + HUD_WIDTH - 2, y, x + HUD_WIDTH, y + HUD_HEIGHT,
    // borderColor); // 右边框
    //
    // // 中间区域
    // guiGraphics.fill(x + 2, y + 2, x + HUD_WIDTH - 2, y + HUD_HEIGHT - 2,
    // 0x44002200); // 深绿色半透明背景
    // }

    private static void renderCameraInfo(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        BlockPos cameraPos = SecurityMonitorBlock.getCurrentCameraPos();
        if (cameraPos == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        String cameraInfo = "CAM: X=" + cameraPos.getX() + " Y=" + cameraPos.getY() + " Z=" + cameraPos.getZ();
        int textWidth = font.width(cameraInfo);
        int x = (screenWidth - textWidth) / 2;
        int y = 18; // 在HUD下方显示信息

        guiGraphics.drawString(font, Component.literal(cameraInfo).withStyle(ChatFormatting.GREEN), x, y, 0xFFFFFFFF,
                false);

        // 绘制一个简单的摄像头图标
        renderCameraIcon(guiGraphics, screenWidth / 2 + HUD_WIDTH / 2 - 15, 15);
    }

    private static void renderCameraIcon(GuiGraphics guiGraphics, int x, int y) {
        // 简单绘制一个摄像头图标
        guiGraphics.fill(x, y, x + 10, y + 6, 0xFF00FF00); // 矩形摄像头本体
        guiGraphics.fill(x + 10, y + 1, x + 14, y + 5, 0xFF008800); // 镜头部分
        // 绘制摄像头指示灯
        guiGraphics.fill(x + 14, y + 2, x + 16, y + 4, shouldBlink ? 0xFFFF0000 : 0xFF00FF00); // 红色/绿色指示灯
    }

    private static void renderExitHint(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        MutableComponent exitHint = Component.translatable("screen.starrailexpress.security_monitor.exit",
                Component.keybind("key.sneak"));
        int textWidth = font.width(exitHint);
        int x = (screenWidth - textWidth) / 2;
        int y = screenHeight - 36; // 屏幕底部

        guiGraphics.drawString(font, exitHint.withStyle(ChatFormatting.GRAY), x, y, 0xFFFFFFFF,
                true);
    }

    public static void renderCameraFeed(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (!SecurityMonitorBlock.isInSecurityMode() || !SREClientConfig.instance().enableSecurityCameraHUD) {
            return;
        }

        PoseStack poseStack = guiGraphics.pose();
        int feedWidth = screenWidth / 4;
        int feedHeight = screenHeight / 4;
        int x = 10; // 左上角
        int y = 10;

        // 绘制摄像头画面区域的边框
        int borderColor = shouldBlink ? 0xFFFF0000 : 0xFF00FF00;
        guiGraphics.fill(x, y, x + feedWidth, y + 2, borderColor); // 顶边框
        guiGraphics.fill(x, y, x + 2, y + feedHeight, borderColor); // 左边框
        guiGraphics.fill(x, y + feedHeight - 2, x + feedWidth, y + feedHeight, borderColor); // 底边框
        guiGraphics.fill(x + feedWidth - 2, y, x + feedWidth, y + feedHeight, borderColor); // 右边框

        // 绘制摄像头画面内容（模拟效果）
        guiGraphics.fill(x + 2, y + 2, x + feedWidth - 2, y + feedHeight - 2, 0xFF111111); // 深灰色背景

        // 绘制"SECURITY FEED"文字
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        String feedTitle = "SECURITY MONITOR";
        int titleWidth = font.width(feedTitle);
        int titleX = x + (feedWidth - titleWidth) / 2;
        int titleY = y + 8;

        guiGraphics.drawString(font, Component.literal(feedTitle).withStyle(ChatFormatting.GREEN), titleX, titleY,
                0xFFFFFFFF, false);

        // 绘制摄像头编号
        String camNumber = "CAM 01";
        int numberWidth = font.width(camNumber);
        int numberX = x + (feedWidth - numberWidth) / 2;
        int numberY = y + 20;

        guiGraphics.drawString(font, Component.literal(camNumber).withStyle(ChatFormatting.AQUA), numberX, numberY,
                0xFFFFFFFF, false);

        // 绘制时间戳
        String timeString = getCurrentTimeString();
        int timeWidth = font.width(timeString);
        int timeX = x + (feedWidth - timeWidth) / 2;
        int timeY = y + feedHeight - 15;

        guiGraphics.drawString(font, Component.literal(timeString).withStyle(ChatFormatting.GRAY), timeX, timeY,
                0xFFFFFFFF, false);
    }

    private static void renderStatusIndicator(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        String status = "STREAMING";
        int textWidth = font.width(status);
        int x = (screenWidth - textWidth) / 2;
        int y = 35; // 在摄像头信息下方

        // 绘制状态背景
        guiGraphics.fill(x - 5, y - 2, x + textWidth + 5, y + font.lineHeight + 2, 0x88000000); // 半透明黑色背景
        guiGraphics.drawString(font, Component.literal(status).withStyle(ChatFormatting.RED), x, y, 0xFFFFFFFF, false);
    }

    private static String getCurrentTimeString() {
        // 简单的时间戳，实际实现可能需要更精确的时间
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        long seconds = (gameTime / 20) % 60;
        long minutes = (gameTime / (20 * 60)) % 60;
        long hours = (gameTime / (20 * 60 * 60)) % 24;
        return String.format("%02d:%02d:%02d", (int) hours, (int) minutes, (int) seconds);
    }

    private static void updateBlinkEffect() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlinkSwitchTime > 500) { // 每0.5秒切换一次
            shouldBlink = !shouldBlink;
            lastBlinkSwitchTime = currentTime;
        }
    }

    public static void onCameraSwitch() {
        lastBlinkSwitchTime = System.currentTimeMillis();
        shouldBlink = true;
    }
}