package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public final class MapStatusBarHudRenderer {
    private static final ResourceLocation WARMTH_ICON = Noellesroles.id("stamina/warmth_icon");
    private static final ResourceLocation THIRST_ICON = Noellesroles.id("stamina/thirst_icon");
    private static final ResourceLocation HUNGER_ICON = ResourceLocation.fromNamespaceAndPath("minecraft",
            "hud/food_full");

    private static final int BAR_WIDTH = 120;
    private static final int BAR_HEIGHT = 2;
    private static final int ICON_SIZE = 9;
    private static final int ICON_GAP = 4;

    /** 低于此比例触发屏幕边框效果 */
    private static final float LOW_THRESHOLD = 0.35f;
    private static final float LOW_THRESHOLD_1 = 0.3f;
    private static final float LOW_THRESHOLD_2 = 0.25f;
    private static final float LOW_THRESHOLD_3 = 0.2f;
    private static final float LOW_THRESHOLD_4 = 0.15f;
    private static final float LOW_THRESHOLD_5 = 0.1f;
    /** 边框颜色：口渴=蓝色、保暖=雪色、饥饿=棕色 */
    private static final int THIRST_EDGE_COLOR = 0xFF4488FF;
    private static final int WARMTH_EDGE_COLOR = 0xFFFFFAFA;
    private static final int HUNGER_EDGE_COLOR = 0xFFC89632;
    private static long lastWarnedTime = 0;

    private MapStatusBarHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) {
            return;
        }
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(client.player.level());
        if (gameComponent == null || !gameComponent.isRunning()) {
            return;
        }
        // 自由相机/观战状态下不渲染，和体力条一致
        if (!SREClient.isPlayerAliveAndInSurvival()) {
            return;
        }
        MapStatusBarType type = MapStatusBarClientState.type();
        if (type == MapStatusBarType.NONE) {
            return;
        }
        type = SREClient.areaComponent.areasSettings.mapStatusBar;
        if (type == MapStatusBarType.NONE) {
            return;
        }

        float value = (float) MapStatusBarClientState.value() / MapStatusBarClientState.maxValue();

        // 低于35%时触发屏幕边框效果
        if (value < LOW_THRESHOLD) {
            int warnGap = 20; // 1s = 20tick
            float intensity = 0.5f;
            if (value < LOW_THRESHOLD_5) {
                warnGap = 1;
                intensity = 1f;
            } else if (value < LOW_THRESHOLD_4) {
                warnGap = 5;
                intensity = 0.9f;
            } else if (value < LOW_THRESHOLD_3) {
                warnGap = 10;
                intensity = 0.8f;
            } else if (value < LOW_THRESHOLD_2) {
                warnGap = 15;
                intensity = 0.7f;
            } else if (value < LOW_THRESHOLD_1) {
                warnGap = 18;
                intensity = 0.6f;
            }
            long nowTime = client.level.getGameTime();
            if (lastWarnedTime <= 0 || nowTime - lastWarnedTime >= warnGap) {
                int edgeColor = switch (type) {
                    case WARMTH -> WARMTH_EDGE_COLOR;
                    case THIRST -> THIRST_EDGE_COLOR;
                    case HUNGER -> HUNGER_EDGE_COLOR;
                    default -> 0xFFFFFFFF;
                };
                StaminaRenderer.triggerScreenEdgeEffect(edgeColor, 100, intensity);
                lastWarnedTime = nowTime;
            }
        } else {
            lastWarnedTime = 0;
        }

        int barX = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int barY = graphics.guiHeight() - 28;
        int iconX = barX - ICON_SIZE - ICON_GAP;
        int iconY = barY - ICON_SIZE / 2 + BAR_HEIGHT / 2;

        // 绘制图标——默认常驻显示
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
            return 0xFFDD3333; // 红色 (低于1/5)
        }
        return switch (type) {
            case WARMTH -> 0xFFFF8830; // 橙色
            case THIRST -> 0xFF4488FF; // 蓝色
            case HUNGER -> 0xFFC89632; // 棕黄色
            default -> 0xFFFFFFFF;
        };
    }
}
