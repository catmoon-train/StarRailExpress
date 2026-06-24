package org.agmas.noellesroles.client.hud;

import io.wifi.starrailexpress.game.data.MapStatusBarType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public final class MapStatusBarHudRenderer {
    private static final ResourceLocation WARMTH_FULL = Noellesroles.id("map_status/warmth_full");
    private static final ResourceLocation WARMTH_HALF = Noellesroles.id("map_status/warmth_half");
    private static final ResourceLocation WARMTH_EMPTY = Noellesroles.id("map_status/warmth_empty");
    private static final ResourceLocation THIRST_FULL = Noellesroles.id("map_status/thirst_full");
    private static final ResourceLocation THIRST_HALF = Noellesroles.id("map_status/thirst_half");
    private static final ResourceLocation THIRST_EMPTY = Noellesroles.id("map_status/thirst_empty");
    private static final ResourceLocation FOOD_FULL = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/food_full");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/food_half");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.fromNamespaceAndPath("minecraft", "hud/food_empty");

    private MapStatusBarHudRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) {
            return;
        }
        MapStatusBarType type = MapStatusBarClientState.type();
        if (type == MapStatusBarType.NONE) {
            return;
        }

        int value = Math.round((MapStatusBarClientState.value() / (float) MapStatusBarClientState.maxValue()) * 20.0F);
        int right = graphics.guiWidth() / 2 + 91 - 9;
        int y = graphics.guiHeight() - 32;

        for (int i = 0; i < 10; i++) {
            int x = right - i * 8;
            int units = value - i * 2;
            graphics.blitSprite(empty(type), x, y, 9, 9);
            if (units >= 2) {
                graphics.blitSprite(full(type), x, y, 9, 9);
            } else if (units == 1) {
                graphics.blitSprite(half(type), x, y, 9, 9);
            }
        }
    }

    private static ResourceLocation full(MapStatusBarType type) {
        return switch (type) {
            case WARMTH -> WARMTH_FULL;
            case THIRST -> THIRST_FULL;
            case HUNGER -> FOOD_FULL;
            default -> FOOD_EMPTY;
        };
    }

    private static ResourceLocation half(MapStatusBarType type) {
        return switch (type) {
            case WARMTH -> WARMTH_HALF;
            case THIRST -> THIRST_HALF;
            case HUNGER -> FOOD_HALF;
            default -> FOOD_EMPTY;
        };
    }

    private static ResourceLocation empty(MapStatusBarType type) {
        return switch (type) {
            case WARMTH -> WARMTH_EMPTY;
            case THIRST -> THIRST_EMPTY;
            case HUNGER -> FOOD_EMPTY;
            default -> FOOD_EMPTY;
        };
    }
}
