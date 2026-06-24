package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public final class MapStatusBarHudRenderer {
    private static final ResourceLocation WARMTH_ICON = Noellesroles.id("stamina/warmth_icon");
    private static final ResourceLocation THIRST_ICON = Noellesroles.id("stamina/thirst_icon");
    private static final ResourceLocation HUNGER_ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/food_full");

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 2;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 4;

    private MapStatusBarHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        // 与体力条同步：仅在游戏运行时渲染
        if (!SREClient.shouldUseTrainHud()) {
            return;
        }
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(client.player.level());
        if (gameComponent == null || !gameComponent.isRunning()) {
            return;
        }
        MapStatusBarType type = MapStatusBarClientState.type();
        if (type == MapStatusBarType.NONE) {
            return;
        }

        float value = (float) MapStatusBarClientState.value() / MapStatusBarClientState.maxValue();
        int barX = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int barY = graphics.guiHeight() - 28;
        int iconX = barX - ICON_SIZE - ICON_GAP;
        int iconY = barY - ICON_SIZE / 2 + BAR_HEIGHT / 2;

        // 绘制图标
        graphics.blitSprite(icon(type), iconX, iconY, ICON_SIZE, ICON_SIZE);

        // 绘制背景条
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0x66000000);

        // 绘制填充条
        int fillWidth = Math.round(BAR_WIDTH * value);
        if (fillWidth > 0) {
            graphics.fill(barX, barY, barX + fillWidth, barY + BAR_HEIGHT, color(type, value));
        }
    }

    private static ResourceLocation icon(MapStatusBarType type) {
        return switch (type) {
            case WARMTH -> WARMTH_ICON;
            case THIRST -> THIRST_ICON;
            case HUNGER -> HUNGER_ICON;
            default -> HUNGER_ICON;
        };
    }

    private static int color(MapStatusBarType type, float value) {
        if (value < 0.2f) {
            return 0xFFDD3333;   // 红色 (低于1/5)
        }
        return switch (type) {
            case WARMTH -> 0xFFFF8830;   // 橙色
            case THIRST -> 0xFF4488FF;   // 蓝色
            case HUNGER -> 0xFFC89632;   // 棕黄色
            default -> 0xFFFFFFFF;
        };
    }
}
