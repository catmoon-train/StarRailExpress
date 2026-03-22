package io.wifi.starrailexpress.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

@Environment(EnvType.CLIENT)
public class ScopeOverlayRenderer {
    private static boolean inScopeView = false;

    public static boolean isInScopeView() {
        return inScopeView;
    }

    public static void setInScopeView(boolean inScopeView) {
        ScopeOverlayRenderer.inScopeView = inScopeView;
        if (inScopeView) {
            Minecraft.getInstance().options.smoothCamera = true;
        } else{
            Minecraft.getInstance().options.smoothCamera = false;
        }
    }

    public static void renderScopeOverlay(GuiGraphics context, DeltaTracker tickCounter) {
        if (!inScopeView)
            return;

        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int viewRadius = Math.min(screenWidth, screenHeight) / 3; // 可视区域半径

        context.pose().pushPose();

        // 启用混合和深度测试
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 渲染圆形蒙版（四周遮蔽，中间圆形空白）
        int margin = 10; // 额外边距确保完全覆盖
        int coverRadius = Math.max(screenWidth, screenHeight) / 2 + margin;

        // 使用多个矩形覆盖四个角落和边缘，形成圆形遮蔽
        // 上部分
        context.fill(0, 0, screenWidth, centerY - viewRadius, 0xFF000000);
        // 下部分
        context.fill(0, centerY + viewRadius, screenWidth, screenHeight, 0xFF000000);

        // 左右部分（上下已被覆盖，只需覆盖中间）
        for (int y = centerY - viewRadius; y <= centerY + viewRadius; y++) {
            int xOffset = (int) Math.sqrt(viewRadius * viewRadius - (y - centerY) * (y - centerY));
            // 左边
            context.fill(0, y, centerX - xOffset, y + 1, 0xFF000000);
            // 右边
            context.fill(centerX + xOffset, y, screenWidth, y + 1, 0xFF000000);
        }

        // 渲染倍镜准星（十字线）
        int crosshairThickness = 2;
        int crosshairLength = 15;

        // 水平线
        context.fill(centerX - crosshairLength, centerY - crosshairThickness / 2,
                centerX + crosshairLength, centerY + crosshairThickness / 2, 0xFFFFFFFF);

        // 垂直线
        context.fill(centerX - crosshairThickness / 2, centerY - crosshairLength,
                centerX + crosshairThickness / 2, centerY + crosshairLength, 0xFFFFFFFF);

        // 渲染倍镜圆圈（准星圈）
        int circleRadius = viewRadius;
        int circleThickness = 2;
        for (int angle = 0; angle < 360; angle += 1) {
            double rad = Math.toRadians(angle);
            int x1 = centerX + (int) (Math.cos(rad) * (circleRadius - circleThickness / 2));
            int y1 = centerY + (int) (Math.sin(rad) * (circleRadius - circleThickness / 2));
            int x2 = centerX + (int) (Math.cos(rad) * (circleRadius + circleThickness / 2));
            int y2 = centerY + (int) (Math.sin(rad) * (circleRadius + circleThickness / 2));
            context.fill(x1, y1, x2 + 1, y2 + 1, 0xFFDDDDDD);
        }

        // 渲染刻度线（垂直方向）
        int tickLength = 8;
        int tickThickness = 1;
        for (int i = -3; i <= 3; i++) {
            if (i == 0)
                continue; // 跳过中心
            int tickY = centerY + i * 12;
            context.fill(centerX - tickLength / 2, tickY - tickThickness / 2,
                    centerX + tickLength / 2, tickY + tickThickness / 2, 0xFFAAAAAA);
        }

        // 渲染刻度线（水平方向）
        for (int i = -3; i <= 3; i++) {
            if (i == 0)
                continue; // 跳过中心
            int tickX = centerX + i * 12;
            context.fill(tickX - tickThickness / 2, centerY - tickLength / 2,
                    tickX + tickThickness / 2, centerY + tickLength / 2, 0xFFAAAAAA);
        }

        context.pose().popPose();
        RenderSystem.disableBlend();
    }
}
