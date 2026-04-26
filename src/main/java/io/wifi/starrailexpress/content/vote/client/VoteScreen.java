package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.content.vote.ClientPlayerOption;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * 星穹铁道投票界面 —— 深空科技风格
 * <p>
 * 特性：
 * - 带渐变边框的主面板，角落 L 形装饰
 * - 标题两侧装饰线，计时器徽章随剩余时间变色与闪烁
 * - 选项按钮模拟金属质感，左侧彩色指示条，底部投票进度条
 * - 多选时至少保留一项选择，防止空提交
 * - 滚动条带渐变滑块，确认按钮绿金配色
 */
public class VoteScreen extends Screen {

    // ── 布局常量 ───────────────────────────────────────────────────────────────
    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 28;
    private static final int BUTTON_SPACING = 3;
    private static final int CONTENT_Y = 82;
    private static final int SCROLL_WIDTH = 5;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ICON_SIZE = 16;
    private static final int CONFIRM_W = 120;
    private static final int CONFIRM_H = 22;
    // private static final int CONFIRM_Y_OFF = 12;

    // 面板横向内边距
    private static final int PANEL_PAD = 22;

    // ── 调色盘：星穹铁道 深空主题 ────────────────────────────────────────────
    // 背景
    private static final int COL_OVERLAY = 0xD40A1120;
    // 面板
    private static final int COL_PANEL_BG_TOP = 0xF20C1828;
    private static final int COL_PANEL_BG_BOT = 0xF2060D18;
    private static final int COL_PANEL_BORDER = 0xFF152E4E;
    private static final int COL_PANEL_SHINE = 0xFF2AAAD4;
    private static final int COL_CORNER_MARK = 0xFF1A6090;
    // 标题区
    private static final int COL_TITLE = 0xFFD8EFFF;
    private static final int COL_DIVIDER = 0xFF1A3A58;
    private static final int COL_DIVIDER_BRIGHT = 0xFF1E5080;
    // 计时器
    private static final int COL_TIMER_NORMAL = 0xFF1ABCCC;
    private static final int COL_TIMER_WARN = 0xFFFFAA33;
    private static final int COL_TIMER_URGENT_A = 0xFFFF5555;
    private static final int COL_TIMER_URGENT_B = 0xFFFF2222;
    private static final int COL_TIMER_BADGE_BG = 0xFF060F1C;
    private static final int COL_TIMER_PAUSED = 0xFF6A90A8;
    // 按钮：普通
    private static final int COL_BTN_TOP = 0xFF0D2035;
    private static final int COL_BTN_BOT = 0xFF081628;
    // 按钮：悬停
    private static final int COL_BTN_HOV_TOP = 0xFF183855;
    private static final int COL_BTN_HOV_BOT = 0xFF0F2840;
    // 按钮：选中
    private static final int COL_BTN_SEL_TOP = 0xFF0C3A46;
    private static final int COL_BTN_SEL_BOT = 0xFF07242E;
    // 按钮边框
    private static final int COL_BTN_BOR = 0xFF18374F;
    private static final int COL_BTN_BOR_HOV = 0xFF287AAA;
    private static final int COL_BTN_BOR_SEL = 0xFF1ABCCC;
    // 左侧高亮条
    private static final int COL_ACCENT_TEAL = 0xFF1ABCCC;
    private static final int COL_ACCENT_BLUE = 0xFF1E6A9A;
    // 进度条
    private static final int COL_BAR_BG = 0xFF071020;
    private static final int COL_BAR_FG_TOP = 0xFF1A7EAA;
    private static final int COL_BAR_FG_BOT = 0xFF0F4E70;
    private static final int COL_BAR_SEL_TOP = 0xFF1AAA88;
    private static final int COL_BAR_SEL_BOT = 0xFF0D6A55;
    // 文字
    private static final int COL_TEXT_PRIMARY = 0xFFE0F4FF;
    private static final int COL_TEXT_HOVER = 0xFFFFFFFF;
    private static final int COL_TEXT_SELECTED = 0xFFCCF8FF;
    private static final int COL_TEXT_NORMAL = 0xFFB0D0E8;
    private static final int COL_TEXT_MUTED = 0xFF4A7090;
    private static final int COL_TEXT_HINT = 0xFF6A90A8;
    // 勾号
    private static final int COL_CHECK = 0xFF22DD70;
    // 确认按钮
    private static final int COL_CONFIRM_BOR_OFF = 0xFF1A2E40;
    private static final int COL_CONFIRM_BOR_ON = 0xFF1A8050;
    private static final int COL_CONFIRM_BOR_HOV = 0xFF22DD70;
    private static final int COL_CONFIRM_BG_TOP = 0xFF0E4030;
    private static final int COL_CONFIRM_BG_BOT = 0xFF082820;
    private static final int COL_CONFIRM_HOV_TOP = 0xFF1A6040;
    private static final int COL_CONFIRM_HOV_BOT = 0xFF104030;
    // 滚动条
    private static final int COL_SCROLL_TRACK = 0xFF0A1825;
    private static final int COL_SCROLL_TOP = 0xFF3A7AAA;
    private static final int COL_SCROLL_BOT = 0xFF2A5A80;
    private static final int COL_SCROLL_EDGE = 0xFF4AAFDF;
    // 可重投提示
    private static final int COL_REVOTE = 0xFF22CC6A;

    // ── 状态字段 ───────────────────────────────────────────────────────────────
    private int contentX;
    private int panelX, panelY, panelW, panelH;
    private int tickCounter = 0;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private final List<WidgetButton> buttons = new ArrayList<>();
    private boolean hasVoted = false;

    private final Set<Integer> selectedIndices = new LinkedHashSet<>();
    private boolean multiSelectMode;
    private int maxSelect;

    public VoteScreen() {
        super(ClientVoteCache.getTitle());
    }

    // ── 初始化 & 生命周期 ─────────────────────────────────────────────────────
    @Override
    protected void init() {
        this.multiSelectMode = ClientVoteCache.getMaxSelectCount() > 1;
        this.maxSelect = ClientVoteCache.getMaxSelectCount();
        if (!ClientVoteCache.isAllowReVote() || !hasVoted) {
            selectedIndices.clear();
            hasVoted = false;
        }
        updateLayout();
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        tickCounter++;
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        updateLayout();
        rebuildWidgets();
    }

    private void updateLayout() {
        contentX = (width - BUTTON_WIDTH) / 2;
        panelW = BUTTON_WIDTH + PANEL_PAD * 2;
        panelX = (width - panelW) / 2;
        panelY = 10;
        panelH = height - panelY - 10;
    }

    public void updateData(VoteSyncS2CPacket packet) {
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        buttons.clear();
        List<VoteOption> options = ClientVoteCache.getOptions();
        for (int i = 0; i < options.size(); i++) {
            buttons.add(new WidgetButton(i));
        }
        int totalContent = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        int available = scrollAreaH();
        maxScroll = Math.max(0, totalContent - available);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    // 修改：动态计算滚动区域高度
    private int scrollAreaH() {
        int base = height - CONTENT_Y - 30; // 原固定预留 30
        if (showConfirmButton()) {
            base -= (CONFIRM_H + 16); // 再减去确认按钮占用的高度
        }
        return base;
    }

    private int getRemainingSeconds() {
        return ClientVoteCache.getRemainingSeconds();
    }

    // ── 主渲染 ──────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景与覆盖
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, COL_OVERLAY);
        super.render(g, mouseX, mouseY, partialTick);

        int scrollH = scrollAreaH();

        // 主面板
        drawPanel(g);

        // 标题、计时器
        drawHeader(g);

        // 多选提示（列表上方）
        if (multiSelectMode && !hasVoted) {
            Component hint = Component.translatable("vote.multi_select_hint", maxSelect, selectedIndices.size());
            g.drawCenteredString(font, hint, contentX + BUTTON_WIDTH / 2, CONTENT_Y - 14, COL_TEXT_HINT);
        }

        // 选项列表（裁剪区域）
        g.enableScissor(contentX, CONTENT_Y, contentX + BUTTON_WIDTH, CONTENT_Y + scrollH);
        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            btn.render(g, mouseX, mouseY, drawY, selectedIndices.contains(btn.optionIndex));
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            drawScrollbar(g, scrollH);
        }

        // 确认按钮（多选 & 不可重投时显示）
        if (multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote()) {
            drawConfirmButton(g, mouseX, mouseY, scrollH);
        }

        // 可重投提示（屏幕底部）
        if (hasVoted && ClientVoteCache.isAllowReVote()) {
            Component revote = Component.translatable("vote.can_revote").withStyle(ChatFormatting.GREEN);
            g.drawCenteredString(font, revote, width / 2, panelY + panelH - 12, COL_REVOTE);
        }

        // 物品悬停提示
        drawY = CONTENT_Y - scrollOffset;
        for (int i = 0; i < buttons.size(); i++) {
            VoteOption opt = ClientVoteCache.getOptions().get(i);
            if (opt instanceof VoteOption.ItemOption itemOpt) {
                if (mouseX >= contentX && mouseX < contentX + BUTTON_WIDTH &&
                        mouseY >= drawY && mouseY < drawY + BUTTON_HEIGHT) {
                    g.renderTooltip(font, itemOpt.stack(), mouseX, mouseY);
                    break;
                }
            }
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
    }

    // ── 面板与装饰元素 ────────────────────────────────────────────────────────

    /** 主面板：渐变背景、边框、高光线、角落 L 形装饰 */
    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = panelW, h = panelH;

        // 外阴影
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x28000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x40000814);

        // 边框
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_PANEL_BORDER);

        // 渐变填充
        g.fillGradient(x, y, x + w, y + h, COL_PANEL_BG_TOP, COL_PANEL_BG_BOT);

        // 顶部高光线
        g.fill(x, y, x + w, y + 1, COL_PANEL_SHINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x181ABCCC);

        // 角落 L 形装饰
        int cs = 8;
        int cc = COL_CORNER_MARK;
        // 左上
        g.fill(x - 1, y - 1, x - 1 + cs, y, cc);
        g.fill(x - 1, y - 1, x, y - 1 + cs, cc);
        // 右上
        g.fill(x + w + 1 - cs, y - 1, x + w + 1, y, cc);
        g.fill(x + w, y - 1, x + w + 1, y - 1 + cs, cc);
        // 左下
        g.fill(x - 1, y + h, x - 1 + cs, y + h + 1, cc);
        g.fill(x - 1, y + h + 1 - cs, x, y + h + 1, cc);
        // 右下
        g.fill(x + w + 1 - cs, y + h, x + w + 1, y + h + 1, cc);
        g.fill(x + w, y + h + 1 - cs, x + w + 1, y + h + 1, cc);
    }

    /** 标题、装饰线、计时器徽章 */
    private void drawHeader(GuiGraphics g) {
        // 标题
        g.drawCenteredString(font, title, width / 2, panelY + 14, COL_TITLE);

        // 两侧装饰横线
        int titleW = font.width(title);
        int lineY = panelY + 14 + 6;
        int leftEnd = width / 2 - titleW / 2 - 6;
        int rightSt = width / 2 + titleW / 2 + 6;
        if (leftEnd > panelX + 8) {
            g.fill(panelX + 8, lineY, leftEnd, lineY + 1, COL_DIVIDER);
            g.fill(panelX + 8, lineY + 1, panelX + 8 + 20, lineY + 2, COL_DIVIDER_BRIGHT);
        }
        if (rightSt < panelX + panelW - 8) {
            g.fill(rightSt, lineY, panelX + panelW - 8, lineY + 1, COL_DIVIDER);
            g.fill(panelX + panelW - 8 - 20, lineY, panelX + panelW - 8, lineY + 2, COL_DIVIDER_BRIGHT);
        }

        // 计时器徽章
        int sec = getRemainingSeconds();
        String timeStr = sec >= 0 ? formatTime(sec) : "PAUSED";
        int timerColor;
        if (sec < 0) {
            timerColor = COL_TIMER_PAUSED;
        } else if (sec <= 10) {
            timerColor = (tickCounter % 20 < 10) ? COL_TIMER_URGENT_A : COL_TIMER_URGENT_B;
        } else if (sec <= 30) {
            timerColor = COL_TIMER_WARN;
        } else {
            timerColor = COL_TIMER_NORMAL;
        }

        Component timerComp = Component.literal("⏱ " + timeStr);
        int tw = font.width(timerComp) + 12;
        int tx = width / 2 - tw / 2;
        int ty = panelY + 28;
        int badgeH = 13;
        g.fill(tx, ty, tx + tw, ty + badgeH, COL_TIMER_BADGE_BG);
        int topLineColor = (timerColor & 0x00FFFFFF) | 0x99000000;
        g.fill(tx, ty, tx + tw, ty + 1, topLineColor);
        g.drawCenteredString(font, timerComp, width / 2, ty + (badgeH - 8) / 2 + 1, timerColor);

        // 列表区上方分隔线
        int sepY = CONTENT_Y - 6;
        g.fill(panelX + 4, sepY, panelX + panelW - 4, sepY + 1, COL_DIVIDER);
        g.fill(panelX + 4, sepY + 1, panelX + panelW - 4, sepY + 2, 0x10FFFFFF);
    }

    /** 滚动条 */
    private void drawScrollbar(GuiGraphics g, int scrollH) {
        int sx = contentX + BUTTON_WIDTH + 5;
        int total = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        double ratio = (double) scrollH / total;
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
        int thumbY = CONTENT_Y + (int) ((scrollH - thumbH) * ((double) scrollOffset / maxScroll));

        g.fill(sx, CONTENT_Y, sx + SCROLL_WIDTH, CONTENT_Y + scrollH, COL_SCROLL_TRACK);
        g.fillGradient(sx, thumbY, sx + SCROLL_WIDTH, thumbY + thumbH, COL_SCROLL_TOP, COL_SCROLL_BOT);
        g.fill(sx, thumbY, sx + 1, thumbY + thumbH, COL_SCROLL_EDGE);
    }

    /** 确认按钮（多选 & 不可重投） */
    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, int scrollH) {
        int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
        int by = CONTENT_Y + scrollH + 8; // 原 CONFIRM_Y_OFF 改为固定 8 像素间距
        boolean canConfirm = !selectedIndices.isEmpty();
        boolean hovered = canConfirm
                && mouseX >= bx && mouseX < bx + CONFIRM_W
                && mouseY >= by && mouseY < by + CONFIRM_H;

        int bgTop, bgBot, borderColor;
        if (!canConfirm) {
            bgTop = 0xFF111C28;
            bgBot = 0xFF0B1420;
            borderColor = COL_CONFIRM_BOR_OFF;
        } else if (hovered) {
            bgTop = COL_CONFIRM_HOV_TOP;
            bgBot = COL_CONFIRM_HOV_BOT;
            borderColor = COL_CONFIRM_BOR_HOV;
        } else {
            bgTop = COL_CONFIRM_BG_TOP;
            bgBot = COL_CONFIRM_BG_BOT;
            borderColor = COL_CONFIRM_BOR_ON;
        }

        g.fillGradient(bx, by, bx + CONFIRM_W, by + CONFIRM_H, bgTop, bgBot);
        g.renderOutline(bx, by, CONFIRM_W, CONFIRM_H, borderColor);

        if (canConfirm) {
            g.fill(bx + 1, by, bx + CONFIRM_W - 1, by + 1, hovered ? 0x4022DD70 : 0x2022CC6A);
        }

        int textColor = canConfirm ? COL_TEXT_PRIMARY : COL_TEXT_MUTED;
        Component confirmText = Component.translatable("vote.confirm");
        g.drawCenteredString(font, confirmText, bx + CONFIRM_W / 2, by + (CONFIRM_H - 8) / 2, textColor);
    }

    // ── 交互处理 ──────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 确认按钮
        if (showConfirmButton()) {
            int scrollH = scrollAreaH();
            int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
            int by = CONTENT_Y + scrollH + 8;
            if (mouseX >= bx && mouseX < bx + CONFIRM_W
                    && mouseY >= by && mouseY < by + CONFIRM_H) {
                if (!selectedIndices.isEmpty())
                    castMultiVote();
                return true;
            }
        }

        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, drawY)) {
                if (multiSelectMode) {
                    if (hasVoted && !ClientVoteCache.isAllowReVote())
                        return true;

                    // 取消选择：禁止清空所有选项
                    if (selectedIndices.contains(btn.optionIndex)) {
                        if (selectedIndices.size() <= 1) {
                            // 至少保留一项，播放拒绝音效
                            minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                            return true;
                        }
                        selectedIndices.remove(btn.optionIndex);
                    } else {
                        if (selectedIndices.size() < maxSelect) {
                            selectedIndices.add(btn.optionIndex);
                        } else {
                            minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                            return true;
                        }
                    }
                    // 可重投模式下自动提交
                    if (ClientVoteCache.isAllowReVote())
                        castMultiVote();
                    return true;
                } else {
                    // 单选逻辑不变
                    if (hasVoted && !ClientVoteCache.isAllowReVote())
                        return true;
                    selectedIndices.clear();
                    selectedIndices.add(btn.optionIndex);
                    castVote(btn.optionIndex);
                    return true;
                }
            }
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) scrollY * (BUTTON_HEIGHT + BUTTON_SPACING), 0, maxScroll);
        }
        return true;
    }

    private void castVote(int optionIndex) {
        if (hasVoted && !ClientVoteCache.isAllowReVote())
            return;
        ClientPlayNetworking.send(new VoteCastC2SPacket(List.of(optionIndex)));
        afterVote();
    }

    private void castMultiVote() {
        if (hasVoted && !ClientVoteCache.isAllowReVote())
            return;
        if (selectedIndices.isEmpty())
            return;
        ClientPlayNetworking.send(new VoteCastC2SPacket(new ArrayList<>(selectedIndices)));
        afterVote();
    }

    private void afterVote() {
        hasVoted = true;
        playClickSound();
        if (!ClientVoteCache.isAllowReVote())
            onClose();
    }

    private void playClickSound() {
        minecraft.getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String formatTime(int totalSeconds) {
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    // 新增：判断是否需要显示确认按钮
    private boolean showConfirmButton() {
        return multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote();
    }

    // ── 选项按钮内部类 ────────────────────────────────────────────────────────
    private class WidgetButton {
        final int optionIndex;

        WidgetButton(int index) {
            this.optionIndex = index;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, int baseY, boolean selected) {
            int x = contentX, y = baseY, w = BUTTON_WIDTH, h = BUTTON_HEIGHT;

            // 不可见则跳过
            if (y + h < CONTENT_Y || y > CONTENT_Y + scrollAreaH())
                return;

            boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            // 背景渐变
            int bgTop = selected ? COL_BTN_SEL_TOP : (hovered ? COL_BTN_HOV_TOP : COL_BTN_TOP);
            int bgBot = selected ? COL_BTN_SEL_BOT : (hovered ? COL_BTN_HOV_BOT : COL_BTN_BOT);
            g.fillGradient(x, y, x + w, y + h, bgTop, bgBot);

            // 边框
            int borderColor = selected ? COL_BTN_BOR_SEL : (hovered ? COL_BTN_BOR_HOV : COL_BTN_BOR);
            g.renderOutline(x, y, w, h, borderColor);

            // 左侧彩色指示条
            if (selected) {
                g.fill(x, y, x + 2, y + h, COL_ACCENT_TEAL);
            } else if (hovered) {
                g.fill(x, y, x + 2, y + h, COL_ACCENT_BLUE);
            }

            // 顶部高光
            if (hovered || selected) {
                int shine = selected ? 0x281ABCCC : 0x18FFFFFF;
                g.fill(x + 1, y + 1, x + w - 1, y + 2, shine);
            }

            // 进度条（若显示结果）
            if (ClientVoteCache.isShowResults()) {
                Map<Integer, Integer> results = ClientVoteCache.getResults();
                int totalVotes = results.values().stream().mapToInt(Integer::intValue).sum();
                int votes = results.getOrDefault(optionIndex, 0);
                float pct = totalVotes > 0 ? (float) votes / totalVotes : 0f;
                int barW = (int) ((w - 4) * pct);

                g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, COL_BAR_BG);
                if (barW > 0) {
                    int ft = selected ? COL_BAR_SEL_TOP : COL_BAR_FG_TOP;
                    int fb = selected ? COL_BAR_SEL_BOT : COL_BAR_FG_BOT;
                    g.fillGradient(x + 2, y + h - 3, x + 2 + barW, y + h - 1, ft, fb);
                }

                String voteStr = String.valueOf(votes);
                int voteRight = x + w - 6 - (selected ? 14 : 0);
                g.drawString(font, voteStr, voteRight - font.width(voteStr), y + (h - 8) / 2, COL_TEXT_MUTED);
            }

            // 图标/头像
            VoteOption option = ClientVoteCache.getOptions().get(optionIndex);
            boolean hasIcon = option instanceof VoteOption.ItemOption || option instanceof ClientPlayerOption;
            int iconX = x + 10;

            if (option instanceof VoteOption.ItemOption itemOpt) {
                g.renderFakeItem(itemOpt.stack(), iconX, y + (h - ICON_SIZE) / 2);
            } else if (option instanceof ClientPlayerOption playerOpt) {
                UUID uuid = playerOpt.uuid();
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    PlayerFaceRenderer.draw(g, info.getSkin(), iconX, y + (h - ICON_SIZE) / 2, ICON_SIZE);
                }
            }

            // 文字
            Component display = option.display();
            int textColor = selected ? COL_TEXT_SELECTED : (hovered ? COL_TEXT_HOVER : COL_TEXT_NORMAL);
            if (hasIcon) {
                g.drawString(font, display, iconX + ICON_SIZE + 6, y + (h - 8) / 2, textColor);
            } else {
                g.drawCenteredString(font, display, x + w / 2, y + (h - 8) / 2, textColor);
            }

            // 勾号（选中标记）
            if (selected) {
                // 微小的缩放动画，基于时间
                float pulse = 1.0f + 0.05f * Mth.sin((tickCounter * 0.15f) % Mth.TWO_PI);
                int checkSize = (int) (10 * pulse);
                int checkX = x + w - 18;
                int checkY = y + (h - checkSize) / 2;
                g.drawCenteredString(font, "✔", checkX + checkSize / 2, checkY, COL_CHECK);
            }
        }

        boolean mouseClicked(double mx, double my, int baseY) {
            return mx >= contentX && mx < contentX + BUTTON_WIDTH
                    && my >= baseY && my < baseY + BUTTON_HEIGHT;
        }
    }
}