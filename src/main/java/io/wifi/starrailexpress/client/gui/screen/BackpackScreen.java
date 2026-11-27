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

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.backpack.BackpackState;
import io.wifi.starrailexpress.backpack.FactionCardCooldown;
import io.wifi.starrailexpress.backpack.PendingCardState;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.client.ui.SRETheme;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 场外背包界面：展示各类阵营卡的持有量与状态，点击卡片即使用（走 {@code sre:pass activate} 命令）。
 *
 * <h2>这一版做了什么</h2>
 * <ul>
 * <li><b>统一色板 / 阵营色</b>：面板走 {@link SRETheme}，阵营强调色走
 * {@link RoleTeam#color()}（唯一权威来源），不再各写一份色值。</li>
 * <li><b>状态可见</b>：顶部条显示「本局待生效的卡 → 目标阵营」，每行显示可用 / 冷却 / 已使用，
 * 并明确写出「分不到目标阵营会自动退卡」，让玩家知道卡不会白花。</li>
 * <li><b>自定义卡</b>：点击弹出 {@link FactionCardTargetScreen} 选目标阵营，选完回到本界面。</li>
 * <li><b>自适应行高</b>：5 行按屏幕高度收缩，小窗口也不会把底部提示顶出画面。</li>
 * </ul>
 *
 * <p>
 * 数据来自 {@link ClientPlayerDataCache}：卡牌计数（{@code backpack} 分区）与待生效卡
 * （{@code card_force} 分区，由服务端 {@code BackpackManager} 推送）。
 */
public class BackpackScreen extends Screen {

    /** 展示顺序与各行的强调色（自定义卡用金色）。 */
    private static final FactionCardType[] DISPLAY_ORDER = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER,
            FactionCardType.CUSTOM };

    // ---- 布局（4px 栅格）----
    private static final int PAD = 8;
    private static final int HEADER_H = 36;
    private static final int STATUS_H = 20;
    private static final int FOOTER_H = 20;
    private static final int ROW_GAP = 4;
    private static final int ROW_H_MIN = 26;
    private static final int ROW_H_MAX = 44;
    private static final int BAR_H = 2;

    /** 每多少帧重读一次缓存（服务端推包是异步的，别每帧解析 JSON）。 */
    private static final int REFRESH_FRAMES = 10;

    private final LocalPlayer player;
    private final Screen parent;

    private BackpackState backpack;
    private PendingCardState pending;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowX;
    private int rowW;
    private int rowH;

    private int refreshCountdown;

    public BackpackScreen(Screen parent) {
        super(Component.translatable("sre.backpack.title"));
        this.player = Minecraft.getInstance().player;
        this.parent = parent;
        refresh(true);
    }

    @Override
    protected void init() {
        super.init();
        refresh(true);
    }

    // =========================================================================
    // 数据
    // =========================================================================

    private void refresh(boolean force) {
        if (!force && --refreshCountdown > 0) {
            return;
        }
        refreshCountdown = REFRESH_FRAMES;
        backpack = ClientPlayerDataCache.backpack(player.getUUID());
        pending = ClientPlayerDataCache.pendingCard(player.getUUID());
    }

    private int count(FactionCardType type) {
        return backpack.cards.getOrDefault(type, 0);
    }

    private static int accentOf(FactionCardType type) {
        return switch (type) {
            case KILLER -> RoleTeam.KILLER.color();
            case CIVILIAN -> RoleTeam.CIVILIAN.color();
            case NEUTRAL -> RoleTeam.NEUTRAL.color();
            case NEUTRAL_FOR_KILLER -> RoleTeam.NEUTRAL_KILLER.color();
            default -> SRETheme.GOLD;
        };
    }

    // =========================================================================
    // 布局
    // =========================================================================

    private void computeLayout() {
        int rows = DISPLAY_ORDER.length;
        int fixed = PAD * 2 + HEADER_H + STATUS_H + FOOTER_H;
        int availableRows = height - 24 - fixed - ROW_GAP * (rows - 1);
        rowH = Mth.clamp(availableRows / rows, ROW_H_MIN, ROW_H_MAX);
        panelW = Mth.clamp((int) (width * 0.58F), 300, 420);
        panelH = fixed + rows * rowH + ROW_GAP * (rows - 1);
        panelX = (width - panelW) / 2;
        panelY = Math.max(4, (height - panelH) / 2);
        rowX = panelX + PAD;
        rowW = panelW - PAD * 2;
    }

    private int rowY(int index) {
        return panelY + PAD + HEADER_H + STATUS_H + index * (rowH + ROW_GAP);
    }

    // =========================================================================
    // 渲染
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refresh(false);
        computeLayout();
        super.render(g, mouseX, mouseY, partialTick);

        drawPanel(g);

        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            renderCardRow(g, i, mouseX, mouseY);
        }

        renderFooter(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics g) {
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, SRETheme.SURFACE_TOP,
                SRETheme.SURFACE_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, SRETheme.BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, SRETheme.DECOR_LINE);

        // ---- 标题 + 总持有量 ----
        g.drawCenteredString(font, title, panelX + panelW / 2, panelY + PAD + 2, SRETheme.TITLE);
        int total = totalCards();
        g.drawCenteredString(font, Component.translatable("sre.backpack.subtitle", total),
                panelX + panelW / 2, panelY + PAD + 16, SRETheme.MUTED);

        // ---- 本局待生效的卡 ----
        int statusY = panelY + PAD + HEADER_H;
        int statusH = STATUS_H - 4;
        if (pending.active()) {
            FactionCardType target = pending.targetType();
            int accent = accentOf(target);
            g.fillGradient(rowX, statusY, rowX + rowW, statusY + statusH,
                    SRETheme.blendColors(SRETheme.SURFACE_TOP, accent, 0.30F),
                    SRETheme.blendColors(SRETheme.SURFACE_BOTTOM, accent, 0.16F));
            g.renderOutline(rowX, statusY, rowW, statusH, SRETheme.GOLD);
        } else {
            g.fill(rowX, statusY, rowX + rowW, statusY + statusH, SRETheme.SCROLL_TRACK);
        }
        Component status = pending.active()
                ? Component.translatable("sre.backpack.active", Component.translatable(pending.targetType().displayName))
                : Component.translatable("sre.backpack.idle");
        g.drawCenteredString(font, status, panelX + panelW / 2, statusY + (statusH - 8) / 2,
                pending.active() ? SRETheme.TEXT : SRETheme.MUTED);
    }

    private void renderCardRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        FactionCardType type = DISPLAY_ORDER[index];
        int accent = accentOf(type);
        int count = count(type);
        boolean owned = count > 0;
        long remaining = FactionCardCooldown.remainingMs(backpack, type);
        boolean cooling = remaining > 0L;
        boolean inUse = pending.active() && pending.cardType() == type;
        boolean usable = owned && !cooling;
        int x = rowX;
        int y = rowY(index);
        boolean hovered = inside(mouseX, mouseY, x, y, rowW, rowH);

        // 行底：可用为阵营色渐变，悬停更亮，其余压暗
        if (inUse) {
            g.fillGradient(x, y, x + rowW, y + rowH, SRETheme.SELECTED_TOP, SRETheme.SELECTED_BOTTOM);
        } else if (hovered && usable) {
            g.fillGradient(x, y, x + rowW, y + rowH,
                    SRETheme.blendColors(SRETheme.SURFACE_TOP, accent, 0.42F),
                    SRETheme.blendColors(SRETheme.SURFACE_BOTTOM, accent, 0.24F));
        } else if (usable) {
            g.fillGradient(x, y, x + rowW, y + rowH,
                    SRETheme.blendColors(SRETheme.SURFACE_TOP, accent, 0.22F),
                    SRETheme.blendColors(SRETheme.SURFACE_BOTTOM, accent, 0.10F));
        } else {
            g.fill(x, y, x + rowW, y + rowH, SRETheme.SCROLL_TRACK);
        }
        int border = inUse ? SRETheme.GOLD : (hovered && usable ? SRETheme.GOLD
                : (usable ? SRETheme.CARD_BORDER : SRETheme.blendColors(SRETheme.CARD_BORDER, SRETheme.BLACK, 0.4F)));
        g.renderOutline(x, y, rowW, rowH, border);
        // 左侧阵营色条
        g.fill(x + 1, y + 1, x + 4, y + rowH - 1, usable || inUse ? accent : SRETheme.withAlpha(accent, 0x66));

        // 卡名
        g.drawString(font, Component.translatable(type.displayName), x + 12, y + 5,
                usable || inUse ? SRETheme.TEXT : SRETheme.MUTED, false);
        // 状态副行
        Component state = stateLine(type, owned, cooling, inUse, remaining);
        g.drawString(font, state, x + 12, y + 17, stateColor(owned || inUse, cooling, inUse), false);
        // 右侧计数
        String countText = "×" + count;
        g.drawString(font, countText, x + rowW - 12 - font.width(countText), y + (rowH - 8) / 2,
                owned ? accent : SRETheme.MUTED, false);

        // 冷却进度条
        if (cooling) {
            int barY = y + rowH - BAR_H - 1;
            float ratio = cooldownRatio(type, remaining);
            g.fill(x + 1, barY, x + rowW - 1, barY + BAR_H, SRETheme.SCROLL_TRACK);
            int filled = (int) ((rowW - 2) * (1.0F - ratio));
            g.fill(x + 1, barY, x + 1 + filled, barY + BAR_H, SRETheme.BLUE);
        }
    }

    private Component stateLine(FactionCardType type, boolean owned, boolean cooling, boolean inUse,
            long remaining) {
        if (inUse) {
            return Component.translatable("sre.backpack.state_in_use");
        }
        if (cooling) {
            return Component.translatable("sre.backpack.cooldown", FactionCardCooldown.formatRemaining(remaining));
        }
        if (!owned) {
            return Component.translatable("sre.backpack.unavailable");
        }
        return Component.translatable(type == FactionCardType.CUSTOM
                ? "sre.backpack.pick_target"
                : "sre.backpack.click_activate");
    }

    private static int stateColor(boolean owned, boolean cooling, boolean inUse) {
        if (inUse) {
            return SRETheme.GOLD;
        }
        if (cooling) {
            return SRETheme.BLUE;
        }
        return owned ? SRETheme.GREEN : SRETheme.MUTED;
    }

    private float cooldownRatio(FactionCardType type, long remaining) {
        long full = FactionCardCooldown.remainingMs(backpack, type) + (System.currentTimeMillis()
                - backpack.lastUsedAt(type));
        if (full <= 0L) {
            return 0.0F;
        }
        return Mth.clamp((float) remaining / (float) full, 0.0F, 1.0F);
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int y = panelY + panelH - FOOTER_H;
        Component left = totalCards() > 0 ? Component.translatable("sre.backpack.hint")
                : Component.translatable("sre.backpack.empty");
        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            if (inside(mouseX, mouseY, rowX, rowY(i), rowW, rowH)) {
                left = Component.translatable("sre.backpack.desc." + DISPLAY_ORDER[i].questKey);
                break;
            }
        }
        g.drawString(font, left, rowX, y + 6, SRETheme.MUTED, false);
        Component close = Component.translatable("sre.backpack.close_hint");
        g.drawString(font, close, rowX + rowW - font.width(close), y + 6, SRETheme.MUTED, false);
    }

    /** 背包里一共持有多少张阵营卡。 */
    private int totalCards() {
        int total = 0;
        for (FactionCardType type : DISPLAY_ORDER) {
            total += count(type);
        }
        return total;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, SRETheme.SCREEN_TOP, SRETheme.SCREEN_BOTTOM);
    }

    // =========================================================================
    // 输入
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < DISPLAY_ORDER.length; i++) {
                FactionCardType type = DISPLAY_ORDER[i];
                if (count(type) <= 0 || FactionCardCooldown.isOnCooldown(backpack, type)
                        || !inside(mouseX, mouseY, rowX, rowY(i), rowW, rowH)) {
                    continue;
                }
                if (type == FactionCardType.CUSTOM) {
                    // 自定义卡先选目标阵营，选完回到本界面
                    if (minecraft != null) {
                        minecraft.setScreen(new FactionCardTargetScreen(this));
                    }
                } else {
                    sendCommand("sre:pass activate " + type.questKey);
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
