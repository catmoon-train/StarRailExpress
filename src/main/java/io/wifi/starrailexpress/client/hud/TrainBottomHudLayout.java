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

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.StaminaRenderer;
import io.wifi.starrailexpress.client.render.hud.stamina.utils.HotbarCooldownRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.agmas.noellesroles.client.hud.MapStatusBarHudRenderer;

/**
 * 列车 HUD 底部（快捷栏正上方）的纵向排版。
 *
 * <p>
 * 之前体力条、地图状态条（污染值等）各自把一个固定的 {@code guiHeight() - 35 / -28}
 * 写在渲染器里，两根条同时出现时几乎贴在一起（中间只有几像素），
 * 其它浮层（手持物品提示、主手冷却百分比）也是各写一个固定高度，
 * 于是叠在一起互相压字。这里统一成一根“竖列”：
 *
 * <pre>
 *   ┌ 手持物品提示（多行）           ← heldTooltipBottomY 往上排
 *   ├ 主手冷却百分比                 ← mainHandCooldownTextY
 *   ├ 状态条 1（污染值等）           ← barY(g, statusBarSlot())
 *   ├ 状态条 0（体力 / 蓄力）        ← barY(g, staminaBarSlot())
 *   ├ 快捷栏冷却秒数（guiHeight-32）
 *   └ 快捷栏（guiHeight-22 起）
 * </pre>
 *
 * 条与条之间固定 {@link #BAR_PITCH} 的间距，宽度统一 {@link #BAR_WIDTH}、高度
 * {@link #BAR_HEIGHT}，所以两根条同时出现也不会显得拥挤。
 */
public final class TrainBottomHudLayout {

    /** 所有状态条统一宽度（以屏幕水平中线居中）。 */
    public static final int BAR_WIDTH = 120;
    /** 状态条高度（比旧的 2px 略厚一点，样式更统一）。 */
    public static final int BAR_HEIGHT = 3;
    /** 相邻两根状态条的中心线间距（= 条高 3 + 留白 5）。 */
    public static final int BAR_PITCH = 8;
    /** 状态条左侧图标尺寸 / 与条的间距；图标默认隐藏，按住 Shift 才出现。 */
    public static final int BAR_ICON_SIZE = 9;
    public static final int BAR_ICON_GAP = 4;

    /**
     * 最下层状态条（{@link #staminaBarSlot()}）中心线距屏幕底部的高度。
     * 快捷栏冷却秒数画在底部 32px，这里再往上留 6px，避免贴字。
     */
    private static final int BASE_BAR_CENTER_FROM_BOTTOM = 38;

    private TrainBottomHudLayout() {
    }

    /** 从下往上第 {@code slot} 根状态条的中心线 Y（slot 0 = 最下面那根）。 */
    public static int barCenterY(GuiGraphics graphics, int slot) {
        return graphics.guiHeight() - BASE_BAR_CENTER_FROM_BOTTOM - Math.max(0, slot) * BAR_PITCH;
    }

    /** 从下往上第 {@code slot} 根状态条的顶边 Y（配合 {@link #BAR_HEIGHT} 使用 fill）。 */
    public static int barY(GuiGraphics graphics, int slot) {
        return barCenterY(graphics, slot) - BAR_HEIGHT / 2;
    }

    /** 从下往上第 {@code slot} 根状态条左侧图标的 Y。 */
    public static int barIconY(GuiGraphics graphics, int slot) {
        return barCenterY(graphics, slot) - BAR_ICON_SIZE / 2;
    }

    /** 状态条左侧图标的 X（条左端再往左一格）。 */
    public static int barIconX(GuiGraphics graphics) {
        return graphics.guiWidth() / 2 - BAR_WIDTH / 2 - BAR_ICON_SIZE - BAR_ICON_GAP;
    }

    /** 体力 / 蓄力条固定占最下面一格。 */
    public static int staminaBarSlot() {
        return 0;
    }

    /** 地图状态条（污染值等）：有体力条时让到上面一格，否则自己占用最下面一格。 */
    public static int statusBarSlot() {
        return hasStaminaBar() ? 1 : 0;
    }

    /**
     * 本帧体力条是否真的会画出来。
     *
     * <p>
     * 体力条只在列车 HUD 下渲染（见 {@code InGameHudMixin}），所以除了体力数据本身，
     * 还要确认列车 HUD 处于启用状态，否则地图状态条会让出一格留下空档。
     */
    public static boolean hasStaminaBar() {
        if (!SREClient.shouldUseTrainHud()) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        return StaminaRenderer.willRenderBar(minecraft == null ? null : minecraft.player);
    }

    /** 地图状态条本帧是否会出现。 */
    public static boolean hasStatusBar() {
        return MapStatusBarHudRenderer.isVisible();
    }

    /** 当前同时占用的状态条数量（体力条 + 地图状态条）。 */
    public static int visibleBarCount() {
        int count = hasStaminaBar() ? 1 : 0;
        if (hasStatusBar()) {
            count++;
        }
        return count;
    }

    /**
     * 状态条竖列顶部之上、留给文字信息（手持物品提示 / 主手冷却百分比）的底边。
     * 文字从这个 Y 往上排，因此永远不会压到条上。
     */
    public static int infoBlockBottomY(GuiGraphics graphics) {
        int count = Math.max(1, visibleBarCount());
        // barY 是条的最上一行，再留 6px 空隙给文字
        return barY(graphics, count - 1) - 6;
    }

    /**
     * 手持物品提示文字块的底边。
     *
     * <p>
     * 依次避让：状态条竖列 → 主手冷却百分比那一行 → 原版 actionbar（{@code guiHeight - 68}）。
     * 原版 actionbar 由原版负责画，位置固定，所以这里反推它的上边界，让提示始终排在它之上。
     */
    public static int heldTooltipBottomY(GuiGraphics graphics) {
        int bottom = infoBlockBottomY(graphics);
        Minecraft minecraft = Minecraft.getInstance();
        int lineHeight = minecraft == null ? 9 : minecraft.font.lineHeight;
        if (HotbarCooldownRenderer.isMainHandCooldownTextVisible()) {
            bottom -= lineHeight + 2;
        }
        // 原版 actionbar（displayClientMessage(msg, true)）画在 guiHeight - 68，占一行
        if (minecraft != null && minecraft.gui != null && minecraft.gui.overlayMessageTime > 0) {
            int actionBarTop = graphics.guiHeight() - 68;
            bottom = Math.min(bottom, actionBarTop - 2);
        }
        return bottom;
    }

    /**
     * 主手冷却百分比文字的 Y（文字顶边）。
     *
     * <p>
     * 它排在信息区最下面一行，正好占住 {@link #infoBlockBottomY} 之上的那一行；
     * 手持物品提示再往它上面排，两者不会重叠，也都不压状态条。
     */
    public static int mainHandCooldownTextY(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        int lineHeight = minecraft == null ? 9 : minecraft.font.lineHeight;
        return infoBlockBottomY(graphics) - lineHeight;
    }

    /**
     * 状态条图标是否显示：默认隐藏，只保留 bar；按住 Shift 才显示图标。
     * 可以通过客户端配置恢复“常驻显示图标”。
     */
    public static boolean showBarIcons() {
        SREClientConfig config = SREClientConfig.instance();
        if (config != null && !config.statusBarIconsOnShiftOnly) {
            return true;
        }
        return Screen.hasShiftDown();
    }
}
