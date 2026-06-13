package io.wifi.starrailexpress.client.hud;

import io.wifi.starrailexpress.content.item.TimedGrenadeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * 滞时雷手持 HUD — 屏幕中央偏下显示剩余引爆秒数。
 * <p>
 * 在 HudRenderCallback 中调用 {@link #render(GuiGraphics, float)}。
 */
public class TimedGrenadeHUD {
    private static final int HUD_COLOR_NORMAL = 0xFFFFFFFF;
    private static final int HUD_COLOR_URGENT = 0xFFFF4444; // 最后 1 秒红色闪烁
    private static final int HUD_BG_COLOR = 0x80000000;

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || client.level == null) return;

        if (!(player.getMainHandItem().getItem() instanceof TimedGrenadeItem)
                && !(player.getOffhandItem().getItem() instanceof TimedGrenadeItem)) {
            return;
        }

        int remainingTicks = TimedGrenadeItem.getRemainingFuse(client.level, player.getUUID());
        if (remainingTicks < 0) return;

        float seconds = remainingTicks / 20.0f;
        String text = String.format("%.1fs", seconds);

        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();
        Font font = client.font;

        int x = screenW / 2;
        int y = screenH / 2 + 60;

        int textWidth = font.width(text);
        int color = remainingTicks <= 20 && (remainingTicks / 4) % 2 == 0
                ? HUD_COLOR_URGENT : HUD_COLOR_NORMAL;

        // 背景底衬
        graphics.fill(x - textWidth / 2 - 6, y - 4, x + textWidth / 2 + 6, y + font.lineHeight + 2,
                HUD_BG_COLOR);

        // 文字居中
        graphics.drawCenteredString(font, text, x, y + 1, color);
    }
}
