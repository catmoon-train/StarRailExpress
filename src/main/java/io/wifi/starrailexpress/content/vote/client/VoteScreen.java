package io.wifi.starrailexpress.content.vote.client;

import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.content.vote.ClientPlayerOption;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.network.VoteCastC2SPacket;
import io.wifi.starrailexpress.content.vote.network.VoteSyncS2CPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
 * 星穹铁道投票界面 —— 黄铜仪表盘主题。
 *
 * 视觉目标：
 * - 深棕底色 + 黄铜边框 + 暖金高光，整体更像精密机械面板。
 * - 标题、状态、选项列表、确认区各自有固定节奏，留白更规整。
 * - 结果展示时按票数排序；已选项保持靠前；最终用原始索引兜底，投票协议不受影响。
 */
public class VoteScreen extends Screen {

    private static final int BUTTON_WIDTH = 304;
    private static final int BUTTON_HEIGHT = 32;
    private static final int BUTTON_SPACING = 5;
    private static final int PANEL_PAD_X = 18;
    private static final int PANEL_PAD_Y = 14;
    private static final int HEADER_H = 58;
    private static final int FOOTER_H = 38;
    private static final int SCROLL_WIDTH = 4;
    private static final int SCROLL_MIN_THUMB = 22;
    private static final int ICON_SIZE = 16;
    private static final int CONFIRM_W = 126;
    private static final int CONFIRM_H = 24;

    // 弃票按钮：固定在屏幕左下角，贴边显示（与玩家列表分离）
    private static final int ABSTAIN_W = 124;
    private static final int ABSTAIN_H = 28;
    private static final int ABSTAIN_MARGIN = 6;

    private static final int COL_OVERLAY_TOP = 0xB8120C06;
    private static final int COL_OVERLAY_BOT = 0xE0080603;

    private static final int COL_SHADOW_SOFT = 0x42000000;
    private static final int COL_SHADOW_HARD = 0x64000000;
    private static final int COL_PANEL_BG_TOP = 0xF3261A0D;
    private static final int COL_PANEL_BG_BOT = 0xF0140D07;
    private static final int COL_PANEL_RIM_DARK = 0xFF5A3815;
    private static final int COL_PANEL_RIM = 0xFFC58A36;
    private static final int COL_PANEL_RIM_HI = 0xFFFFD47A;
    private static final int COL_PANEL_INSET = 0xFF3B2410;
    private static final int COL_PANEL_GROOVE = 0x66100602;

    private static final int COL_TITLE = 0xFFFFE3A3;
    private static final int COL_TEXT_NORMAL = 0xFFE5C98B;
    private static final int COL_TEXT_MUTED = 0xFF98734A;
    private static final int COL_TEXT_DARK = 0xFF2A1809;
    private static final int COL_TEXT_HOVER = 0xFFFFFFFF;
    private static final int COL_TEXT_SELECTED = 0xFFFFF2C8;
    private static final int COL_TEXT_HINT = 0xFFC79C61;

    private static final int COL_BRASS_DARK = 0xFF6E4318;
    private static final int COL_BRASS_DIM = 0xFF9E6B2B;
    private static final int COL_BRASS_LIGHT = 0xFFFFD47A;
    private static final int COL_BRASS_SOFT = 0x33FFD47A;
    private static final int COL_GREEN = 0xFF67D285;

    private static final int COL_TIMER_NORMAL = 0xFFFFD47A;
    private static final int COL_TIMER_WARN = 0xFFFFA640;
    private static final int COL_TIMER_URGENT_A = 0xFFFF6E4A;
    private static final int COL_TIMER_URGENT_B = 0xFFE13C28;
    private static final int COL_TIMER_PAUSED = 0xFFB58A5B;

    private static final int COL_BTN_TOP = 0xFF33210F;
    private static final int COL_BTN_BOT = 0xFF211509;
    private static final int COL_BTN_HOV_TOP = 0xFF4B3217;
    private static final int COL_BTN_HOV_BOT = 0xFF2C1C0C;
    private static final int COL_BTN_SEL_TOP = 0xFF60421D;
    private static final int COL_BTN_SEL_BOT = 0xFF3A260F;
    private static final int COL_BTN_BOR = 0xFF704516;
    private static final int COL_BTN_BOR_HOV = 0xFFD49B45;
    private static final int COL_BTN_BOR_SEL = 0xFFFFD47A;

    private static final int COL_BAR_BG = 0xFF1A1007;
    private static final int COL_BAR_FG_TOP = 0xFFC58A36;
    private static final int COL_BAR_FG_BOT = 0xFF81501D;
    private static final int COL_BAR_SEL_TOP = 0xFFFFD47A;
    private static final int COL_BAR_SEL_BOT = 0xFFD17435;

    private static final int COL_CONFIRM_OFF = 0xFF4A3118;
    private static final int COL_CONFIRM_ON_TOP = 0xFFC58A36;
    private static final int COL_CONFIRM_ON_BOT = 0xFF7F4F1B;
    private static final int COL_CONFIRM_HOV_TOP = 0xFFFFD47A;
    private static final int COL_CONFIRM_HOV_BOT = 0xFFC58A36;

    private int contentX;
    private int contentY;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int tickCounter;
    private int scrollOffset;
    private int maxScroll;
    // ===== 滚动条拖拽相关新增字段 =====
    private boolean draggingScrollbar;
    private double dragStartY;
    private int dragStartOffset;
    // =================================

    private final List<WidgetButton> buttons = new ArrayList<>();
    // 弃票选项（如"跳过/弃票"）单独放在左下角，不进入滚动列表
    private final List<Integer> abstainIndices = new ArrayList<>();
    private boolean hasVoted;

    private final Set<Integer> selectedIndices = new LinkedHashSet<>();
    private boolean multiSelectMode;
    private int maxSelect;

    public VoteScreen() {
        super(ClientVoteCache.getTitle());
    }

    @Override
    protected void init() {
        this.multiSelectMode = ClientVoteCache.getMaxSelectCount() > 1;
        this.maxSelect = ClientVoteCache.getMaxSelectCount();
        if (!ClientVoteCache.isAllowReVote() || !hasVoted) {
            selectedIndices.clear();
            hasVoted = false;
        }

        updateLayout();
        restoreStateFromCache();
        rebuildWidgets(true);
    }

    private void restoreStateFromCache() {
        this.hasVoted = ClientVoteCache.hasVoted();
        this.selectedIndices.clear();

        List<VoteOption> options = ClientVoteCache.getOptions();
        for (int idx : ClientVoteCache.getSelectedIndices()) {
            if (idx >= 0 && idx < options.size()) {
                this.selectedIndices.add(idx);
            }
        }
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
        panelW = BUTTON_WIDTH + PANEL_PAD_X * 2;
        panelH = Math.min(height - 18, Math.max(146, height - 24));
        panelX = (width - panelW) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        contentY = panelY + HEADER_H + PANEL_PAD_Y;
    }

    public void updateData(VoteSyncS2CPacket packet) {
        restoreStateFromCache();
        rebuildWidgets();
    }

    public void rebuildWidgets(boolean init) {
        buttons.clear();
        abstainIndices.clear();
        List<VoteOption> options = ClientVoteCache.getOptions();
        for (int i = 0; i < options.size(); i++) {
            VoteOption opt = options.get(i);
            if (abstainIndices.isEmpty() && opt instanceof VoteOption.TextOption to
                    && (to.resultId().equals("inner.cancel") || to.resultId().equals("inner.skip"))) {
                // 弃票选项（弃票/跳过）单独显示在左下角
                abstainIndices.add(i);
            } else {
                buttons.add(new WidgetButton(i));
            }
        }
        if (SREClientConfig.instance().autoSortVotes) {
            sortButtons();
        }

        int totalContent = buttons.isEmpty() ? 0 : buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        int available = scrollAreaH();
        maxScroll = Math.max(0, totalContent - available);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    private void sortButtons() {
        Map<Integer, Integer> results = ClientVoteCache.getResults();
        boolean showResults = ClientVoteCache.isShowResults();
        if (!showResults)
            return;
        buttons.sort(Comparator
                .comparing((WidgetButton btn) -> !selectedIndices.contains(btn.optionIndex))
                .thenComparing((WidgetButton btn) -> showResults ? -results.getOrDefault(btn.optionIndex, 0) : 0)
                .thenComparingInt(btn -> btn.optionIndex));
    }

    private int scrollAreaH() {
        int footer = showConfirmButton() || (hasVoted && ClientVoteCache.isAllowReVote()) ? FOOTER_H : PANEL_PAD_Y;
        int bottom = panelY + panelH - footer;
        return Math.max(34, bottom - contentY);
    }

    private int getRemainingSeconds() {
        return ClientVoteCache.getRemainingSeconds();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        drawBackdrop(g);
        super.render(g, mouseX, mouseY, partialTick);

        int scrollH = scrollAreaH();
        drawPanel(g);
        drawHeader(g);

        if (multiSelectMode) {
            Component hint = Component.translatable("vote.multi_select_hint", maxSelect, selectedIndices.size());
            drawSmallCaps(g, hint.getString(), contentX, contentY - 12, BUTTON_WIDTH, COL_TEXT_HINT);
        }

        drawOptionList(g, mouseX, mouseY, scrollH);

        if (maxScroll > 0) {
            drawScrollbar(g, scrollH);
        }

        if (showConfirmButton()) {
            drawConfirmButton(g, mouseX, mouseY, scrollH);
        }

        if (!abstainIndices.isEmpty()) {
            drawAbstainButton(g, mouseX, mouseY);
        }

        if (hasVoted && ClientVoteCache.isAllowReVote()) {
            Component revote = Component.translatable("vote.can_revote");
            drawStatusPill(g, revote.getString(), panelY + panelH - 26, COL_GREEN);
        }

        renderOptionTooltip(g, mouseX, mouseY);
    }

    private void drawAbstainButton(GuiGraphics g, int mouseX, int mouseY) {
        // 贴在中间玩家列表面板（panel）的左下角边缘，而不是屏幕边缘
        int bx = panelX + ABSTAIN_MARGIN;
        int by = panelY + panelH - ABSTAIN_MARGIN - ABSTAIN_H;
        boolean hovered = mouseX >= bx && mouseX < bx + ABSTAIN_W && mouseY >= by && mouseY < by + ABSTAIN_H;
        int idx = abstainIndices.get(0);
        boolean selected = selectedIndices.contains(idx);

        g.fill(bx - 1, by - 1, bx + ABSTAIN_W + 1, by + ABSTAIN_H + 1, 0x33000000);
        g.fillGradient(bx, by, bx + ABSTAIN_W, by + ABSTAIN_H,
                selected ? COL_BTN_SEL_TOP : (hovered ? COL_BTN_HOV_TOP : COL_BTN_TOP),
                selected ? COL_BTN_SEL_BOT : (hovered ? COL_BTN_HOV_BOT : COL_BTN_BOT));
        g.renderOutline(bx, by, ABSTAIN_W, ABSTAIN_H,
                selected ? COL_BTN_BOR_SEL : (hovered ? COL_BTN_BOR_HOV : COL_BTN_BOR));
        g.fill(bx + 1, by + 1, bx + ABSTAIN_W - 1, by + 2, hovered || selected ? 0x44FFD47A : 0x18FFD47A);

        VoteOption opt = ClientVoteCache.getOptions().get(idx);
        String label = clipText(opt.display().getString(), ABSTAIN_W - 16);
        int textColor = selected ? COL_TEXT_SELECTED : (hovered ? COL_TEXT_HOVER : COL_TEXT_NORMAL);
        g.drawString(font, label, bx + 10, by + 6, textColor);

        // 已选标记
        if (selected) {
            g.drawCenteredString(font, "✓", bx + ABSTAIN_W - 9, by + 6, COL_BRASS_LIGHT);
        }
        // 实时弃票计数
        if (ClientVoteCache.isShowResults()) {
            int votes = ClientVoteCache.getResults().getOrDefault(idx, 0);
            String voteStr = String.valueOf(votes);
            g.drawString(font, voteStr, bx + ABSTAIN_W - (selected ? 22 : 12) - font.width(voteStr), by + 6,
                    COL_TEXT_MUTED);
        }
    }

    private void drawBackdrop(GuiGraphics g) {
        g.fillGradient(0, 0, width, height, COL_OVERLAY_TOP, COL_OVERLAY_BOT);
        g.fill(0, 0, width, 1, 0x20FFFFFF);
        for (int y = 0; y < height; y += 12) {
            g.fill(0, y, width, y + 1, 0x10000000);
        }
    }

    private void drawPanel(GuiGraphics g) {
        int x = panelX;
        int y = panelY;
        int w = panelW;
        int h = panelH;

        g.fill(x - 5, y - 4, x + w + 5, y + h + 6, COL_SHADOW_SOFT);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, COL_SHADOW_HARD);

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_PANEL_RIM_DARK);
        g.fill(x, y, x + w, y + h, COL_PANEL_RIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, COL_PANEL_INSET);
        g.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, COL_PANEL_BG_TOP, COL_PANEL_BG_BOT);

        g.fill(x + 3, y + 3, x + w - 3, y + 4, COL_PANEL_RIM_HI);
        g.fill(x + 4, y + HEADER_H, x + w - 4, y + HEADER_H + 1, COL_PANEL_RIM_DARK);
        g.fill(x + 4, y + HEADER_H + 1, x + w - 4, y + HEADER_H + 2, COL_BRASS_SOFT);

        drawCornerBolts(g, x, y, w, h);
        drawRulerTicks(g, x + 8, y + HEADER_H + 8, h - HEADER_H - 16);
        drawRulerTicks(g, x + w - 10, y + HEADER_H + 8, h - HEADER_H - 16);
    }

    private void drawCornerBolts(GuiGraphics g, int x, int y, int w, int h) {
        int[][] points = {
                { x + 8, y + 8 },
                { x + w - 11, y + 8 },
                { x + 8, y + h - 11 },
                { x + w - 11, y + h - 11 }
        };

        for (int[] point : points) {
            g.fill(point[0], point[1], point[0] + 3, point[1] + 3, COL_BRASS_DARK);
            g.fill(point[0] + 1, point[1], point[0] + 2, point[1] + 3, COL_BRASS_LIGHT);
            g.fill(point[0], point[1] + 1, point[0] + 3, point[1] + 2, COL_BRASS_LIGHT);
        }
    }

    private void drawRulerTicks(GuiGraphics g, int x, int y, int h) {
        for (int yy = y; yy < y + h; yy += 8) {
            int tick = yy % 24 == 0 ? 5 : 3;
            g.fill(x, yy, x + tick, yy + 1, COL_PANEL_GROOVE);
        }
    }

    private void drawHeader(GuiGraphics g) {
        int centerX = panelX + panelW / 2;
        String titleText = clipText(title.getString(), BUTTON_WIDTH - 44);

        g.fill(panelX + 14, panelY + 12, panelX + panelW - 14, panelY + 13, COL_BRASS_DARK);
        g.fill(panelX + 24, panelY + 13, panelX + panelW - 24, panelY + 14, COL_BRASS_SOFT);

        g.drawCenteredString(font, titleText, centerX, panelY + 20, COL_TITLE);

        int titleW = font.width(titleText);
        int left = centerX - titleW / 2 - 12;
        int right = centerX + titleW / 2 + 12;
        g.fill(panelX + 24, panelY + 24, Math.max(panelX + 24, left), panelY + 25, COL_BRASS_DIM);
        g.fill(Math.min(panelX + panelW - 24, right), panelY + 24, panelX + panelW - 24, panelY + 25, COL_BRASS_DIM);

        int sec = getRemainingSeconds();
        String timeStr = sec >= 0 ? formatTime(sec) : "PAUSED";
        int timerColor = timerColor(sec);
        drawStatusPill(g, timeStr, panelY + 38, timerColor);
    }

    private int timerColor(int sec) {
        if (sec < 0) {
            return COL_TIMER_PAUSED;
        }
        if (sec <= 10) {
            return tickCounter % 20 < 10 ? COL_TIMER_URGENT_A : COL_TIMER_URGENT_B;
        }
        if (sec <= 30) {
            return COL_TIMER_WARN;
        }
        return COL_TIMER_NORMAL;
    }

    private void drawStatusPill(GuiGraphics g, String text, int y, int accent) {
        String clipped = clipText(text, BUTTON_WIDTH - 60);
        int w = font.width(clipped) + 26;
        int x = panelX + (panelW - w) / 2;

        g.fill(x, y, x + w, y + 14, COL_PANEL_RIM_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + 13, 0xFF1B1007);
        g.fill(x + 3, y + 2, x + 8, y + 12, accent);
        g.drawCenteredString(font, clipped, x + w / 2 + 3, y + 3, accent);
    }

    private void drawSmallCaps(GuiGraphics g, String text, int x, int y, int w, int color) {
        String clipped = clipText(text, w - 20);
        g.fill(x + 32, y + 4, x + w - 32, y + 5, COL_BRASS_DARK);
        g.drawCenteredString(font, clipped, x + w / 2, y, color);
    }

    private void drawOptionList(GuiGraphics g, int mouseX, int mouseY, int scrollH) {
        if (buttons.isEmpty()) {
            drawEmptyState(g, scrollH);
            return;
        }

        g.enableScissor(contentX, contentY, contentX + BUTTON_WIDTH, contentY + scrollH);
        int drawY = contentY - scrollOffset;
        for (WidgetButton btn : buttons) {
            btn.render(g, mouseX, mouseY, drawY, selectedIndices.contains(btn.optionIndex));
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        g.disableScissor();
    }

    private void drawEmptyState(GuiGraphics g, int scrollH) {
        int y = contentY + Math.max(0, scrollH / 2 - 12);
        g.fill(contentX, y - 10, contentX + BUTTON_WIDTH, y + 22, 0x66211509);
        g.renderOutline(contentX, y - 10, BUTTON_WIDTH, 32, COL_BTN_BOR);
        g.drawCenteredString(font, Component.literal("NO OPTIONS"), contentX + BUTTON_WIDTH / 2, y + 2, COL_TEXT_MUTED);
    }

    private void drawScrollbar(GuiGraphics g, int scrollH) {
        int sx = contentX + BUTTON_WIDTH + 7;
        int total = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        double ratio = (double) scrollH / total;
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
        int thumbY = contentY + (int) ((scrollH - thumbH) * ((double) scrollOffset / maxScroll));

        g.fill(sx, contentY, sx + SCROLL_WIDTH, contentY + scrollH, 0xFF1A1007);
        g.fillGradient(sx, thumbY, sx + SCROLL_WIDTH, thumbY + thumbH, COL_BRASS_LIGHT, COL_BRASS_DARK);
        g.fill(sx + 1, thumbY + 1, sx + 2, thumbY + thumbH - 1, 0x60FFFFFF);
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, int scrollH) {
        int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
        int by = contentY + scrollH + 8;
        boolean canConfirm = !selectedIndices.isEmpty();
        boolean hovered = canConfirm && mouseX >= bx && mouseX < bx + CONFIRM_W && mouseY >= by
                && mouseY < by + CONFIRM_H;

        if (!canConfirm) {
            g.fill(bx, by, bx + CONFIRM_W, by + CONFIRM_H, 0xFF23170B);
            g.renderOutline(bx, by, CONFIRM_W, CONFIRM_H, COL_CONFIRM_OFF);
            g.drawCenteredString(font, Component.translatable("vote.confirm"), bx + CONFIRM_W / 2, by + 8,
                    COL_TEXT_MUTED);
            return;
        }

        int top = hovered ? COL_CONFIRM_HOV_TOP : COL_CONFIRM_ON_TOP;
        int bot = hovered ? COL_CONFIRM_HOV_BOT : COL_CONFIRM_ON_BOT;
        g.fillGradient(bx, by, bx + CONFIRM_W, by + CONFIRM_H, top, bot);
        g.renderOutline(bx, by, CONFIRM_W, CONFIRM_H, hovered ? COL_BRASS_LIGHT : COL_BRASS_DARK);
        g.fill(bx + 2, by + 2, bx + CONFIRM_W - 2, by + 3, 0x55FFFFFF);
        g.drawCenteredString(font, Component.translatable("vote.confirm"), bx + CONFIRM_W / 2, by + 8, COL_TEXT_DARK);
    }

    private void renderOptionTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int drawY = contentY - scrollOffset;
        for (WidgetButton btn : buttons) {
            VoteOption opt = ClientVoteCache.getOptions().get(btn.optionIndex);
            if (!isInsideOption(mouseX, mouseY, drawY)) {
                drawY += BUTTON_HEIGHT + BUTTON_SPACING;
                continue;
            }

            if (opt instanceof VoteOption.ItemOption itemOpt) {
                var itemStack = itemOpt.stack();
                List<Component> tooltipList = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, itemStack));
                if (opt.description() != null && !opt.description().getString().isBlank()) {
                    tooltipList.addFirst(opt.description());
                }
                g.renderTooltip(font, tooltipList, itemStack.getTooltipImage(), mouseX, mouseY);
            } else if (opt.description() != null && !opt.description().getString().isBlank()) {
                g.renderTooltip(font, font.split(opt.description(), 300), mouseX, mouseY);
            }
            break;
        }
    }

    private boolean isInsideOption(double mouseX, double mouseY, int optionY) {
        return mouseX >= contentX
                && mouseX < contentX + BUTTON_WIDTH
                && mouseY >= optionY
                && mouseY < optionY + BUTTON_HEIGHT
                && mouseY >= contentY
                && mouseY < contentY + scrollAreaH();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            int scrollH = scrollAreaH();
            if (maxScroll <= 0 || scrollH <= 0)
                return true;

            int total = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
            double ratio = (double) scrollH / total;
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
            int trackLength = scrollH - thumbH;

            if (trackLength > 0) {
                double delta = mouseY - dragStartY;
                int newOffset = dragStartOffset + (int) (delta / trackLength * maxScroll);
                scrollOffset = Mth.clamp(newOffset, 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // ------ 滚动条拖拽检测（新增） ------
        if (maxScroll > 0) {
            int scrollH = scrollAreaH();
            int sx = contentX + BUTTON_WIDTH + 7;
            int sy = contentY;
            if (mouseX >= sx && mouseX < sx + SCROLL_WIDTH && mouseY >= sy && mouseY < sy + scrollH) {
                // 计算拇指参数
                int total = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
                double ratio = (double) scrollH / total;
                int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
                int thumbY = sy + (int) ((scrollH - thumbH) * ((double) scrollOffset / maxScroll));

                if (mouseY < thumbY || mouseY > thumbY + thumbH) {
                    // 点击轨道：先跳转
                    double clickFraction = (mouseY - sy - thumbH / 2.0) / (scrollH - thumbH);
                    scrollOffset = Mth.clamp((int) (clickFraction * maxScroll), 0, maxScroll);
                }

                // 开始拖拽
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }
        if (!abstainIndices.isEmpty()) {
            int bx = panelX + ABSTAIN_MARGIN;
            int by = panelY + panelH - ABSTAIN_MARGIN - ABSTAIN_H;
            if (mouseX >= bx && mouseX < bx + ABSTAIN_W && mouseY >= by && mouseY < by + ABSTAIN_H) {
                handleOptionClick(abstainIndices.get(0));
                return true;
            }
        }

        if (showConfirmButton()) {
            int scrollH = scrollAreaH();
            int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
            int by = contentY + scrollH + 8;
            if (mouseX >= bx && mouseX < bx + CONFIRM_W && mouseY >= by && mouseY < by + CONFIRM_H) {
                if (!selectedIndices.isEmpty()) {
                    playClickSound();
                    castMultiVote();
                }
                return true;
            }
        }

        int drawY = contentY - scrollOffset;
        for (WidgetButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, drawY)) {
                handleOptionClick(btn.optionIndex);
                return true;
            }
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleOptionClick(int optionIndex) {
        playClickSound();
        if (multiSelectMode) {
            if (hasVoted && !ClientVoteCache.isAllowReVote()) {
                return;
            }

            if (selectedIndices.contains(optionIndex)) {
                selectedIndices.remove(optionIndex);
            } else if (selectedIndices.size() < maxSelect) {
                selectedIndices.add(optionIndex);
            } else {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                return;
            }

            if (SREClientConfig.instance().autoSortVotes) {
                sortButtons();
            }
            if (ClientVoteCache.isAllowReVote()) {
                castMultiVote();
            }
            return;
        }

        if (hasVoted && !ClientVoteCache.isAllowReVote()) {
            return;
        }
        selectedIndices.clear();
        selectedIndices.add(optionIndex);
        castVote(optionIndex);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp(scrollOffset - (int) scrollY * (BUTTON_HEIGHT + BUTTON_SPACING), 0, maxScroll);
        }
        return true;
    }

    private void castVote(int optionIndex) {
        if (hasVoted && !ClientVoteCache.isAllowReVote()) {
            return;
        }
        ClientPlayNetworking.send(new VoteCastC2SPacket(List.of(optionIndex)));
        ClientVoteCache.onVoteSubmitted(List.of(optionIndex));
        afterVote();
    }

    private void castMultiVote() {
        if (hasVoted && !ClientVoteCache.isAllowReVote()) {
            return;
        }
        if (selectedIndices.isEmpty()) {
            return;
        }

        List<Integer> vote = new ArrayList<>(selectedIndices);
        ClientPlayNetworking.send(new VoteCastC2SPacket(vote));
        ClientVoteCache.onVoteSubmitted(vote);
        afterVote();
    }

    private void afterVote() {
        hasVoted = true;
        if (!ClientVoteCache.isAllowReVote()) {
            onClose();
        }
    }

    private void playClickSound() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
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
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private boolean showConfirmButton() {
        return multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote();
    }

    private String clipText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private class WidgetButton {
        final int optionIndex;

        WidgetButton(int index) {
            this.optionIndex = index;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, int baseY, boolean selected) {
            int x = contentX;
            int y = baseY;
            int w = BUTTON_WIDTH;
            int h = BUTTON_HEIGHT;

            if (y + h < contentY || y > contentY + scrollAreaH()) {
                return;
            }

            boolean hovered = isInsideOption(mouseX, mouseY, y);
            int bgTop = selected ? COL_BTN_SEL_TOP : (hovered ? COL_BTN_HOV_TOP : COL_BTN_TOP);
            int bgBot = selected ? COL_BTN_SEL_BOT : (hovered ? COL_BTN_HOV_BOT : COL_BTN_BOT);
            int borderColor = selected ? COL_BTN_BOR_SEL : (hovered ? COL_BTN_BOR_HOV : COL_BTN_BOR);

            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x33000000);
            g.fillGradient(x, y, x + w, y + h, bgTop, bgBot);
            g.renderOutline(x, y, w, h, borderColor);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, hovered || selected ? 0x44FFD47A : 0x18FFD47A);
            g.fill(x + 5, y + 5, x + 7, y + h - 5, selected ? COL_BRASS_LIGHT : COL_BRASS_DIM);

            VoteOption option = ClientVoteCache.getOptions().get(optionIndex);
            drawOptionIcon(g, option, x + 14, y + (h - ICON_SIZE) / 2);
            drawOptionText(g, option, x, y, w, h, selected, hovered);
            drawResultBar(g, x, y, w, h, selected);
            drawSelectionMark(g, x, y, w, h, selected);
        }

        private void drawOptionIcon(GuiGraphics g, VoteOption option, int iconX, int iconY) {
            if (option instanceof VoteOption.ItemOption itemOpt) {
                g.renderFakeItem(itemOpt.stack(), iconX, iconY);
            } else if (option instanceof ClientPlayerOption playerOpt && minecraft.getConnection() != null) {
                UUID uuid = playerOpt.uuid();
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    PlayerFaceRenderer.draw(g, info.getSkin(), iconX, iconY, ICON_SIZE);
                }
            }
        }

        private void drawOptionText(GuiGraphics g, VoteOption option, int x, int y, int w, int h,
                boolean selected, boolean hovered) {
            boolean hasIcon = option instanceof VoteOption.ItemOption || option instanceof ClientPlayerOption;
            int voteReserve = ClientVoteCache.isShowResults() ? 38 : 0;
            int checkReserve = selected ? 18 : 0;
            int textColor = selected ? COL_TEXT_SELECTED : (hovered ? COL_TEXT_HOVER : COL_TEXT_NORMAL);
            String display = clipText(option.display().getString(),
                    w - (hasIcon ? 62 : 34) - voteReserve - checkReserve);

            if (hasIcon) {
                g.drawString(font, display, x + 36, y + 7, textColor);
            } else {
                g.drawString(font, display, x + 16, y + 7, textColor);
            }
        }

        private void drawResultBar(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
            if (!ClientVoteCache.isShowResults()) {
                return;
            }

            Map<Integer, Integer> results = ClientVoteCache.getResults();
            int totalVotes = Math.max(0, ClientVoteCache.getTotalVotes());
            if (totalVotes <= 0) {
                totalVotes = results.values().stream().mapToInt(Integer::intValue).sum();
            }

            int votes = results.getOrDefault(optionIndex, 0);
            float pct = totalVotes > 0 ? (float) votes / totalVotes : 0f;
            int barX = x + 10;
            int barY = y + h - 7;
            int barW = w - 20;
            int fillW = (int) (barW * pct);

            g.fill(barX, barY, barX + barW, barY + 3, COL_BAR_BG);
            if (fillW > 0) {
                int top = selected ? COL_BAR_SEL_TOP : COL_BAR_FG_TOP;
                int bot = selected ? COL_BAR_SEL_BOT : COL_BAR_FG_BOT;
                g.fillGradient(barX, barY, barX + fillW, barY + 3, top, bot);
            }

            String voteStr = String.valueOf(votes);
            g.drawString(font, voteStr, x + w - 12 - font.width(voteStr), y + 7, COL_TEXT_MUTED);
        }

        private void drawSelectionMark(GuiGraphics g, int x, int y, int w, int h, boolean selected) {
            if (!selected) {
                return;
            }

            float pulse = 1.0f + 0.05f * Mth.sin((tickCounter * 0.15f) % Mth.TWO_PI);
            int markW = (int) (12 * pulse);
            int markX = x + w - 23;
            int markY = y + (h - 12) / 2;

            g.fill(markX, markY, markX + 14, markY + 12, 0xFF2A1809);
            g.renderOutline(markX, markY, 14, 12, COL_BRASS_LIGHT);
            g.drawCenteredString(font, "*", markX + markW / 2 + 1, markY + 2, COL_BRASS_LIGHT);
        }

        boolean mouseClicked(double mx, double my, int baseY) {
            return isInsideOption(mx, my, baseY);
        }
    }
}
