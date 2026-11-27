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
import io.wifi.starrailexpress.client.gui.OpeningPresentationCoordinator;
import io.wifi.starrailexpress.client.gui.anim.GuiAnim;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.client.ui.UiSurface;
import io.wifi.starrailexpress.network.ItemPickupToastPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 左下角拾取物品提示：图标 + 物品名 + 数量，从下往上堆叠滚动，同名物品折叠成一行。
 *
 * <h2>为什么走 Fabric {@code HudRenderCallback} 而不是 {@code FakeHudRenderCallback}</h2>
 * <p>
 * 后者的内容会被 {@code OptimizedTextRenderer} 按 <b>每游戏 tick</b> 缓存重放
 * （见 {@code GuiRenderMixin} 与 {@code OptimizedTextRenderer#endFrame}），也就是 20fps 的阶梯动画。
 * 本提示的入场、上移、合并脉冲、淡出都要求逐帧平滑，所以这里用原版 {@link GuiGraphics}
 * 直接逐帧绘制（与 {@code WaypointHUD}、字幕等已有的逐帧 HUD 同一路子）。
 * 这些绘制不进批处理，因此只在真的有提示时才有开销。
 *
 * <h2>视觉规格</h2>
 * <ul>
 * <li>卡片：{@link SRETheme#CARD_BG} 半透明底 + 1px {@link SRETheme#BORDER} 描边（扁平，无渐变）。</li>
 * <li>左侧 2px 黄铜条：新出现 / 合并时脉冲一次，提示「这一行刚变了」。</li>
 * <li>物品名 {@link SRETheme#TEXT}，数量 {@link SRETheme#BRASS_BRIGHT}（合并后数字滚动增长）。</li>
 * </ul>
 *
 * <h2>动画</h2>
 * <ul>
 * <li>入场：从下方 {@link #ENTER_DROP} 处滑上来，透明度 easeOutCubic 淡入。</li>
 * <li>滚动：每行 Y 用帧率无关的指数平滑逼近目标位置，旧行消失后其余行平滑补位。</li>
 * <li>合并：不新增行，数量滚动累加 + 脉冲 + 停留时间重置。</li>
 * <li>消失：透明度淡出、向左滑出 {@link #EXIT_SLIDE_PX}，结束后才真正移除。</li>
 * </ul>
 */
public final class ItemPickupToastHud {

    /** 同屏最多几行（超出的把最旧的挤掉）。 */
    private static final int MAX_ROWS = 5;
    /** 卡片高度。 */
    private static final int ROW_H = 18;
    /** 行间距。 */
    private static final int ROW_GAP = 2;
    /** 图标尺寸（原版物品图标 16×16）。 */
    private static final int ICON = 16;
    /** 卡片左内边距。 */
    private static final int PAD_LEFT = 3;
    /** 图标右侧到文字的距离。 */
    private static final int ICON_TEXT_GAP = 4;
    /** 物品名与数量之间的最小间隔。 */
    private static final int NAME_COUNT_GAP = 6;
    /** 数量右侧内边距。 */
    private static final int PAD_RIGHT = 5;
    /** 物品名最多占多宽，超出按宽度截断。 */
    private static final int MAX_NAME_W = 132;
    /** 距屏幕左边距。 */
    private static final int MARGIN_LEFT = 6;
    /**
     * 最下面一行距屏幕底边。
     *
     * <p>快捷栏在底部约 22px、其上方 20px 内还有冷却秒数与状态条（见
     * {@link TrainBottomHudLayout}，最低一根条在底部 38px），所以这里取 46：
     * 卡片底边落在状态条上方一点，五层叠起来也不会压到屏幕中央的生命/饥饿条。
     */
    private static final int MARGIN_BOTTOM = 46;
    /** 原版聊天框可见时，整摞提示再上移这么多，避开聊天区（聊天框底边留白 40px）。 */
    private static final int CHAT_LIFT = 44;

    /** 出现 / 淡入时长。 */
    private static final int ENTER_MS = 180;
    /** 停留时长（每次合并刷新）。 */
    private static final int HOLD_MS = 2600;
    /** 淡出 / 滑出时长。 */
    private static final int EXIT_MS = 320;
    /** 入场时的起始下坠距离。 */
    private static final float ENTER_DROP = 12.0F;
    /** 淡出时向左滑出的距离。 */
    private static final float EXIT_SLIDE_PX = 8.0F;
    /** Y 位置收敛速度（每秒），越大追得越紧。 */
    private static final float Y_FOLLOW_SPEED = 15.0F;
    /** 数量滚动收敛速度（每秒）。 */
    private static final float COUNT_FOLLOW_SPEED = 12.0F;
    /** 合并脉冲衰减速度（每秒）。 */
    private static final float PULSE_DECAY_SPEED = 3.2F;

    /** 索引 0 = 屏幕最下面那一行，新的从底部进入。 */
    private static final List<Row> ROWS = new ArrayList<>();
    private static boolean registered;

    private ItemPickupToastHud() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        HudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    /** 收到服务端提示：同名同组件的一条折叠到已有行上。 */
    public static void push(ItemPickupToastPayload payload) {
        if (payload == null) {
            return;
        }
        push(payload.stack());
    }

    public static void push(ItemStack pickedUp) {
        if (pickedUp == null || pickedUp.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        long now = Util.getMillis();
        // 只保留 1 个当"样式模板"（图标 + 显示名 + 组件），数量单独记账。
        ItemStack single = pickedUp.copyWithCount(1);
        int amount = Math.max(1, pickedUp.getCount());

        // 折叠：图标 + 名字 + 组件都相同的算同一项。已进入淡出的旧行不参与合并，
        // 否则会出现"刚要淡出的行又被拉回来、透明度跳变"的观感问题。
        for (Row row : ROWS) {
            if (row.exiting || !ItemStack.isSameItemSameComponents(row.stack, single)) {
                continue;
            }
            row.count += amount;
            row.lastTouchMs = now;
            row.pulse = 1.0F;
            return;
        }

        ROWS.add(0, new Row(single, amount, now));
        // 超出上限的行不硬删：标记成"被挤出"，让它跟着淡出/滑出动画退场，
        // 否则第 6 条一出现，最上面那条会瞬间消失。
        int visible = 0;
        for (Row row : ROWS) {
            if (row.exiting) {
                continue;
            }
            if (++visible > MAX_ROWS) {
                row.exiting = true;
                row.exitStartMs = now;
            }
        }
        // 淡出中的旧行还留在表里，极端情况下再加一道硬上限兜底。
        while (ROWS.size() > MAX_ROWS * 2) {
            ROWS.remove(ROWS.size() - 1);
        }
    }

    /** 切场景 / 退出对局时清空，避免残影带到下一局。 */
    public static void reset() {
        ROWS.clear();
    }

    private static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (ROWS.isEmpty()) {
            return;
        }
        // 这些情况只是"本帧不该画"：跳过绘制但保留状态，
        // 这样松开 F1 / 开场演出结束后提示还在原处继续淡出，不会凭空消失。
        if (client.player == null || client.options.hideGui
                || OpeningPresentationCoordinator.shouldSuppressGameplayHud()) {
            return;
        }
        // 这些是"真的不该有"：退出对局 / 关掉开关 —— 直接丢弃，不等它自己淡出。
        if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || !SREClientConfig.instance().showItemPickupHud) {
            ROWS.clear();
            return;
        }

        Font font = client.font;
        float dt = GuiAnim.frameDeltaSeconds();
        long now = Util.getMillis();
        // 索引 0 在最底下，往上累加。原版聊天框在同区域时整摞上移让位
        //（本模组对局里聊天框多数时候被 ChatHudMixin 关掉，所以平时不抬高）。
        int lift = SREClient.canRenderChatHud() ? CHAT_LIFT : 0;
        float baseY = graphics.guiHeight() - MARGIN_BOTTOM - lift - ROW_H;
        float pitch = ROW_H + ROW_GAP;

        // 第一趟：推进状态、算好本帧的绘制参数，并画卡片底 / 铜条 / 文字。
        // 图标必须留到后面单独画：物品渲染会 flush 整批 GUI 缓冲，
        // 混在中间画会让已经排队但还没提交的填充被二次乘色。
        boolean anyIcon = false;
        // 先全部置为不可见：本帧被跳过（淡到 0）的行不会在第二趟里画出残留图标。
        Iterator<Row> iterator = ROWS.iterator();
        int index = 0;
        for (Row r : ROWS) {
            r.visible = false;
        }
        while (iterator.hasNext()) {
            Row row = iterator.next();
            // 到点开始淡出；淡出起点固定在「最后一次合并 + 停留时长」，不会因为合并而中途跳变。
            if (!row.exiting && now - row.lastTouchMs >= HOLD_MS) {
                row.exiting = true;
                row.exitStartMs = row.lastTouchMs + HOLD_MS;
            }
            if (row.exiting && now - row.exitStartMs >= EXIT_MS) {
                iterator.remove();
                continue;
            }

            float targetY = baseY - index * pitch;
            if (Float.isNaN(row.y)) {
                row.y = targetY + ENTER_DROP;
            } else {
                row.y = GuiAnim.approach(row.y, targetY, Y_FOLLOW_SPEED, dt);
            }
            row.displayCount = GuiAnim.approach(row.displayCount, row.count, COUNT_FOLLOW_SPEED, dt);
            if (Math.abs(row.displayCount - row.count) < 0.05F) {
                row.displayCount = row.count;
            }
            row.pulse = Math.max(0.0F, row.pulse - PULSE_DECAY_SPEED * dt);

            float alphaIn = GuiAnim.easeOutCubic(Mth.clamp((now - row.bornMs) / (float) ENTER_MS, 0.0F, 1.0F));
            float alphaOut = 1.0F;
            float slideOut = 0.0F;
            if (row.exiting) {
                float t = Mth.clamp((now - row.exitStartMs) / (float) EXIT_MS, 0.0F, 1.0F);
                alphaOut = 1.0F - GuiAnim.easeOutCubic(t);
                slideOut = EXIT_SLIDE_PX * GuiAnim.easeInOutCubic(t);
            }
            float alpha = Math.min(alphaIn, alphaOut);
            if (alpha <= 0.01F) {
                index++;
                continue;
            }

            buildLayout(font, row, MARGIN_LEFT - Math.round(slideOut), Math.round(row.y), alpha);
            drawCard(graphics, font, row);
            anyIcon = true;
            index++;
        }
        if (!anyIcon) {
            return;
        }

        // 第二趟：先提交上面攒着的 GUI 填充，再逐个画物品图标。
        // 图标走实体渲染管线，只认 shaderColor、不认顶点 alpha，
        // 所以每个图标前 setColor、画完立刻恢复，避免影响后面的文字着色。
        graphics.flush();
        for (Row row : ROWS) {
            if (!row.visible) {
                continue;
            }
            if (row.drawAlpha < 0.999F) {
                graphics.setColor(1.0F, 1.0F, 1.0F, row.drawAlpha);
                graphics.renderFakeItem(row.stack, row.drawX + PAD_LEFT, row.drawY + (ROW_H - ICON) / 2);
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                graphics.renderFakeItem(row.stack, row.drawX + PAD_LEFT, row.drawY + (ROW_H - ICON) / 2);
            }
        }
    }

    /** 计算本帧这一行的绘制参数（每帧在渲染循环里覆写，不做对象分配）。 */
    private static void buildLayout(Font font, Row row, int x, int y, float alpha) {
        row.visible = true;
        row.drawX = x;
        row.drawY = y;
        row.drawAlpha = alpha;
        row.drawAlphaInt = Mth.clamp((int) (alpha * 255.0F), 0, 255);

        Component name = row.stack.getHoverName();
        int nameW = Math.min(MAX_NAME_W, font.width(name));
        row.drawName = font.plainSubstrByWidth(name.getString(), nameW);
        row.drawCountText = "\u00D7" + Math.round(row.displayCount);
        int countW = font.width(row.drawCountText);
        row.drawCardW = PAD_LEFT + ICON + ICON_TEXT_GAP + nameW + NAME_COUNT_GAP + countW + PAD_RIGHT;
        row.drawCountW = countW;
    }

    /** 卡片底 + 描边 + 左侧铜条 + 物品名与数量。 */
    private static void drawCard(GuiGraphics graphics, Font font, Row row) {
        int x = row.drawX;
        int y = row.drawY;
        int a = row.drawAlphaInt;
        int cardW = row.drawCardW;

        // 底板 + 描边（扁平：纯色填充 + 1px 铜边，不做渐变）。
        UiSurface.card(graphics, x, y, cardW, ROW_H,
                SRETheme.withAlpha(SRETheme.CARD_BG, (int) (a * 0.86F)),
                SRETheme.withAlpha(SRETheme.BORDER, (int) (a * 0.95F)));

        // 左侧黄铜条：合并脉冲时更亮，提示「这一行刚变了」。
        int accentA = Mth.clamp((int) ((0.45F + 0.55F * row.pulse) * a), 0, 255);
        graphics.fill(x + 1, y + 3, x + 3, y + ROW_H - 3, SRETheme.withAlpha(SRETheme.BRASS, accentA));

        int textX = x + PAD_LEFT + ICON + ICON_TEXT_GAP;
        int textY = y + (ROW_H - font.lineHeight) / 2 + 1;
        graphics.drawString(font, row.drawName, textX, textY,
                SRETheme.withAlpha(SRETheme.TEXT, a), true);

        // 数量：合并瞬间提亮一档再回落，强调「数字在涨」。
        int countColor = SRETheme.blendColors(SRETheme.TEXT, SRETheme.BRASS_BRIGHT, 0.35F + 0.65F * row.pulse);
        graphics.drawString(font, row.drawCountText, x + cardW - PAD_RIGHT - row.drawCountW, textY,
                SRETheme.withAlpha(countColor, a), true);
    }

    /** 一行提示的可变状态。 */
    private static final class Row {
        /** 数量固定为 1 的模板栈，只用来取图标与显示名。 */
        final ItemStack stack;
        final long bornMs;
        int count;
        /** 绘制用的平滑数量（合并后滚动到 {@link #count}）。 */
        float displayCount;
        long lastTouchMs;
        /** 平滑后的 Y；{@link Float#NaN} 表示还没落位，首帧直接摆到入场起点。 */
        float y = Float.NaN;
        float pulse = 1.0F;
        boolean exiting;
        long exitStartMs;

        // ── 本帧绘制参数（每帧覆写，避免在渲染循环里 new 对象）──
        /** 本帧是否要画（淡出到不可见时会被跳过）。 */
        boolean visible;
        int drawX;
        int drawY;
        float drawAlpha;
        int drawAlphaInt;
        int drawCardW;
        int drawCountW;
        String drawName = "";
        String drawCountText = "";

        Row(ItemStack stack, int count, long now) {
            this.stack = stack;
            this.count = count;
            this.displayCount = count;
            this.bornMs = now;
            this.lastTouchMs = now;
        }
    }
}
