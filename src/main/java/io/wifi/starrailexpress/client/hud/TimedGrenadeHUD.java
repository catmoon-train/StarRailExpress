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

package io.wifi.starrailexpress.client.hud;

import io.wifi.starrailexpress.content.item.TimedGrenadeItem;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import io.wifi.utils.client.betterrender.OptimizedTextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.player.Player;

/**
 * 滞时雷手持 HUD — 屏幕中央偏下显示剩余引爆秒数。
 * <p>
 * 必须走 {@link io.wifi.utils.client.betterrender.FakeHudRenderCallback}，
 * 否则文字会在 OptimizedTextRenderer 帧生命周期外绘制，表现为读秒偶尔消失。
 */
public class TimedGrenadeHUD {
    private static final int HUD_COLOR_NORMAL = 0xFFFFFFFF;
    private static final int HUD_COLOR_URGENT = 0xFFFF4444; // 最后 1 秒红色闪烁
    private static final int HUD_BG_COLOR = 0x80000000;

    public static void render(FakeGuiGraphics graphics, float partialTick) {
        if (!OptimizedTextRenderer.INSTANCE.isTickDirty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || client.level == null) return;

        int remainingTicks = TimedGrenadeItem.getRemainingFuseForHud(player);
        if (remainingTicks < 0) return;

        float seconds = remainingTicks / 20.0f;
        String text = String.format("%.1fs", seconds);

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        Font font = client.font;

        int x = screenW / 2;
        int y = screenH / 2 + 60;

        int textWidth = font.width(text);
        int color = remainingTicks <= 20 && (remainingTicks / 4) % 2 == 0
                ? HUD_COLOR_URGENT : HUD_COLOR_NORMAL;

        graphics.fill(x - textWidth / 2 - 6, y - 4, x + textWidth / 2 + 6, y + font.lineHeight + 2,
                HUD_BG_COLOR);
        graphics.drawCenteredString(font, text, x, y + 1, color);
    }
}
