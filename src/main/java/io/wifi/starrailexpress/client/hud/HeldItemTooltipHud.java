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

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SREClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 手持物品提示：在快捷栏正上方的信息区显示当前选中物品的名称与多行说明。
 *
 * <p>
 * 移植自 <a href="https://github.com/Fuzss/held-item-tooltips">Held Item Tooltips</a>
 * 的 {@code SelectedItemHandler} 思路（按行数裁剪、超出时给出“还有 N 行”、
 * 有 actionbar 文本时只显示名字），但排版改由 {@link TrainBottomHudLayout} 统一计算：
 * 文字块始终排在状态条竖列之上，因此不会压到体力条 / 污染值条上。
 *
 * <p>
 * 与 mod 内物品说明（{@code TMMItemTooltips}）共用同一条
 * {@link ItemStack#getTooltipLines} 管线，所以 mod 自己的冷却、职业列表等说明
 * 也会同步出现在这里，不需要第二份文案。
 */
public final class HeldItemTooltipHud {

    /** 单行文字间距（原版 lineHeight = 9，多留 1px 呼吸感）。 */
    private static final int LINE_SPACING = 1;
    /** 文字块与其背景框的左右内边距。 */
    private static final int PADDING_X = 2;
    /** 文字块与其背景框的上下内边距。 */
    private static final int PADDING_Y = 2;
    /** 名字行与后续说明行之间多留的间隔（对齐原 mod 的 name 行额外 3px）。 */
    private static final int NAME_EXTRA_GAP = 3;

    private static ItemStack highlightStack = ItemStack.EMPTY;
    private static String highlightName = "";
    private static int highlightSlot = -1;
    private static int remainingTicks;
    private static boolean shiftHeld;

    private static List<Component> cachedLines = List.of();
    private static boolean linesDirty = true;
    /** 距离下一次重建说明文本还剩多少个 tick。 */
    private static int linesAge;

    private HeldItemTooltipHud() {
    }

    /** 是否接管原版「选中物品名」渲染（列车 HUD 下由本类统一绘制）。 */
    public static boolean replacesVanillaSelectedItemName() {
        return configEnabled() && SREClient.shouldUseTrainHud();
    }

    private static boolean configEnabled() {
        SREClientConfig config = SREClientConfig.instance();
        return config == null || config.heldItemTooltip;
    }

    /** 每个客户端 tick 调用：更新计时器与缓存失效。 */
    public static void tick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        LocalPlayer player = minecraft.player;
        boolean shift = Screen.hasShiftDown();
        if (shift != shiftHeld) {
            // 按住 Shift 时状态条开始显示图标，顺手把物品提示也重新亮一次
            shiftHeld = shift;
            remainingTicks = displayTicks();
            linesDirty = true;
        }

        ItemStack selected = player.getInventory().getSelected();
        int slot = player.getInventory().selected;
        boolean sameItem = !highlightStack.isEmpty()
                && ItemStack.isSameItem(selected, highlightStack)
                && selected.getHoverName().getString().equals(highlightName)
                && slot == highlightSlot;
        if (!sameItem) {
            highlightStack = selected.isEmpty() ? ItemStack.EMPTY : selected;
            highlightName = selected.isEmpty() ? "" : selected.getHoverName().getString();
            highlightSlot = slot;
            remainingTicks = highlightStack.isEmpty() ? 0 : displayTicks();
            linesDirty = true;
        } else if (remainingTicks > 0) {
            remainingTicks--;
        }

        // 冷却秒数 / 耐久这类内容要肉眼可见地跟着变，按固定间隔重建即可
        if (!linesDirty && --linesAge <= 0) {
            linesDirty = true;
        }
        if (linesDirty) {
            linesAge = updateInterval();
        }
    }

    private static void reset() {
        highlightStack = ItemStack.EMPTY;
        highlightName = "";
        highlightSlot = -1;
        remainingTicks = 0;
        cachedLines = List.of();
        linesDirty = true;
        shiftHeld = false;
    }

    private static int displayTicks() {
        SREClientConfig config = SREClientConfig.instance();
        return config == null ? 40 : Math.max(0, config.heldItemTooltipTicks);
    }

    private static int maxLines() {
        SREClientConfig config = SREClientConfig.instance();
        return config == null ? 4 : Mth.clamp(config.heldItemTooltipMaxLines, 1, 12);
    }

    private static int updateInterval() {
        SREClientConfig config = SREClientConfig.instance();
        return config == null ? 4 : Math.max(1, config.heldItemTooltipUpdateInterval);
    }

    /** 当前是否真的会画出内容（供布局判断是否需要预留文字区）。 */
    public static boolean isVisible() {
        if (!configEnabled() || !SREClient.shouldUseTrainHud()) {
            return false;
        }
        int ticks = displayTicks();
        if (ticks != 0 && remainingTicks <= 0) {
            return false;
        }
        return !getLines().isEmpty();
    }

    /** 在列车 HUD 底部信息区绘制手持物品提示（须在状态条之后调用）。 */
    public static void render(@NotNull GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        if (!isVisible()) {
            return;
        }
        int ticks = displayTicks();
        int alpha = ticks == 0 ? 255 : (int) Math.min(255.0F, remainingTicks * 255.0F / 10.0F);
        if (alpha <= 0) {
            return;
        }
        List<Component> lines = getLines();
        if (lines.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2;
        int lineHeight = font.lineHeight + LINE_SPACING;
        int totalHeight = 0;
        int maxWidth = 0;
        for (int i = 0; i < lines.size(); i++) {
            totalHeight += lineHeight + (i == 0 && lines.size() > 1 ? NAME_EXTRA_GAP : 0);
            maxWidth = Math.max(maxWidth, font.width(lines.get(i)));
        }
        totalHeight = Math.max(font.lineHeight, totalHeight - LINE_SPACING);

        int bottomY = TrainBottomHudLayout.heldTooltipBottomY(graphics);
        int topY = bottomY - totalHeight;

        int backgroundAlpha = (int) (alpha * minecraft.options.textBackgroundOpacity().get());
        if (backgroundAlpha > 0) {
            graphics.fill(centerX - maxWidth / 2 - PADDING_X, topY - PADDING_Y,
                    centerX + maxWidth / 2 + PADDING_X, bottomY + PADDING_Y,
                    FastColor.ARGB32.color(backgroundAlpha, 0, 0, 0));
        }

        int textColor = FastColor.ARGB32.color(alpha, 255, 255, 255);
        int y = topY;
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawCenteredString(font, lines.get(i), centerX, y, textColor);
            y += lineHeight + (i == 0 && lines.size() > 1 ? NAME_EXTRA_GAP : 0);
        }
    }

    private static List<Component> getLines() {
        if (!linesDirty) {
            return cachedLines;
        }
        cachedLines = buildLines();
        linesDirty = false;
        return cachedLines;
    }

    private static List<Component> buildLines() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.level() == null) {
            return List.of();
        }
        ItemStack stack = player.getInventory().getSelected();
        if (stack.isEmpty()) {
            return List.of();
        }
        int max = maxLines();
        // actionbar（例如状态条警告）出现时只留名字，避免两段文字叠在一起
        // overlayMessageTime 是 Gui 的私有字段，本项目通过 accesswidener 打开
        if (minecraft.gui != null && minecraft.gui.overlayMessageTime > 0) {
            max = 1;
        }
        TooltipFlag flag = minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED
                : TooltipFlag.Default.NORMAL;
        List<Component> full;
        try {
            full = stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, flag);
        } catch (RuntimeException exception) {
            // 第三方物品的 tooltip 可能因为缺数据而抛错；宁可少显示也不要打断 HUD 渲染
            return List.of();
        }
        if (full.isEmpty()) {
            return List.of();
        }
        SREClientConfig config = SREClientConfig.instance();
        boolean moreLinesHint = config == null || config.heldItemTooltipMoreLinesHint;
        int shownCount = Math.min(max, full.size());
        if (full.size() > shownCount && shownCount > 1 && moreLinesHint) {
            // 末行让给“还有 N 行”，和原版潜影盒提示同款
            List<Component> shown = new ArrayList<>(full.subList(0, shownCount - 1));
            shown.add(Component.translatable("container.shulkerBox.more", full.size() - shown.size())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return List.copyOf(shown);
        }
        return List.copyOf(full.subList(0, shownCount));
    }
}
