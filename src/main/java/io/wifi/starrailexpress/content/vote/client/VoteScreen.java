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

@SuppressWarnings("unused")
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
    private static final int CONFIRM_Y_PADDING = 8; // 确认按钮与列表区的间距

    // 面板横向内边距
    private static final int PANEL_PAD = 22;

    // ── 调色盘：星穹铁道 深空主题 (增强) ────────────────────────────────────
    // 背景
    private static final int COL_OVERLAY = 0xD40A1120;
    // 面板
    private static final int COL_PANEL_BG_TOP = 0xF20C1828;
    private static final int COL_PANEL_BG_BOT = 0xF2060D18;
    private static final int COL_PANEL_BORDER = 0xFF152E4E;
    private static final int COL_PANEL_SHINE = 0xFF2AAAD4; // 顶部高光线
    private static final int COL_CORNER_MARK = 0xFF1A6090; // 角落装饰
    // 科技风格网格
    private static final int COL_GRID = 0x081A3040;
    // 标题区
    private static final int COL_TITLE = 0xFFD8EFFF;
    private static final int COL_DIVIDER = 0xFF1A3A58;
    private static final int COL_DIVIDER_BRIGHT = 0xFF1E5080;
    // 星光粒子
    private static final int COL_STAR = 0x40AAE0FF;
    // 计时器
    private static final int COL_TIMER_NORMAL = 0xFF1ABCCC;
    private static final int COL_TIMER_WARN = 0xFFFFAA33;
    private static final int COL_TIMER_URGENT_A = 0xFFFF5555;
    private static final int COL_TIMER_URGENT_B = 0xFFFF2222;
    private static final int COL_TIMER_BADGE_BG = 0xFF060F1C;
    private static final int COL_TIMER_PAUSED = 0xFF6A90A8;
    private static final int COL_TIMER_GLOW = 0x181ABCCC; // 外发光颜色
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
    // 进度条流动光泽
    private static final int COL_BAR_SHINE = 0x30FFFFFF;
    // 文字
    private static final int COL_TEXT_PRIMARY = 0xFFE0F4FF;
    private static final int COL_TEXT_HOVER = 0xFFFFFFFF;
    private static final int COL_TEXT_SELECTED = 0xFFCCF8FF;
    private static final int COL_TEXT_NORMAL = 0xFFB0D0E8;
    private static final int COL_TEXT_MUTED = 0xFF4A7090;
    private static final int COL_TEXT_HINT = 0xFF6A90A8;
    // 勾号
    private static final int COL_CHECK = 0xFF22DD70;
    private static final int COL_CHECK_GLOW = 0x4022DD70;
    // 确认按钮
    private static final int COL_CONFIRM_BOR_OFF = 0xFF1A2E40;
    private static final int COL_CONFIRM_BOR_ON = 0xFF1A8050;
    private static final int COL_CONFIRM_BOR_HOV = 0xFF22DD70;
    private static final int COL_CONFIRM_BG_TOP = 0xFF0E4030;
    private static final int COL_CONFIRM_BG_BOT = 0xFF082820;
    private static final int COL_CONFIRM_HOV_TOP = 0xFF1A6040;
    private static final int COL_CONFIRM_HOV_BOT = 0xFF104030;
    // 确认按钮脉冲发光
    private static final int COL_CONFIRM_PULSE_A = 0x6022DD70;
    private static final int COL_CONFIRM_PULSE_B = 0x301A8050;
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

    // 粒子星星的随机位置缓存 (相对面板坐标)
    private final List<Star> stars = new ArrayList<>();

    public VoteScreen() {
        super(ClientVoteCache.getTitle());
    }

    // ── 初始化 ─────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        this.multiSelectMode = ClientVoteCache.getMaxSelectCount() > 1;
        this.maxSelect = ClientVoteCache.getMaxSelectCount();
        if (!ClientVoteCache.isAllowReVote() || !hasVoted) {
            selectedIndices.clear();
            hasVoted = false;
        }
        generateStars(); // 生成固定星星位置
        updateLayout();
        rebuildWidgets();
    }

    private void generateStars() {
        stars.clear();
        Random rand = new Random(42); // 固定种子保证一致性
        for (int i = 0; i < 20; i++) {
            int sx = panelX + rand.nextInt(panelW);
            int sy = panelY + rand.nextInt(panelH);
            stars.add(new Star(sx, sy, rand.nextFloat() * 360f, rand.nextFloat() * 1.5f + 0.5f));
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
        generateStars();
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
        int available = getScrollAreaHeight(); // 动态高度
        maxScroll = Math.max(0, totalContent - available);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
    }

    /** 根据是否显示确认按钮动态调整列表可视高度 */
    private int getScrollAreaHeight() {
        int base = height - CONTENT_Y - 30;
        if (multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote()) {
            base -= (CONFIRM_H + CONFIRM_Y_PADDING + 8); // 为确认按钮预留空间
        }
        return Math.max(base, BUTTON_HEIGHT + 4);
    }

    /** 用于绘制时保持一致的高度引用 */
    private int scrollAreaH() {
        return getScrollAreaHeight();
    }

    private int getRemainingSeconds() {
        return ClientVoteCache.getRemainingSeconds();
    }

    // ── 渲染 ────────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1. 背景与星空粒子
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, height, COL_OVERLAY);

        // 2. 主面板
        drawPanel(g);

        // 3. 面板周围漂浮的星光 (绘制在面板上方，但不影响交互)
        for (Star star : stars) {
            float alpha = (Mth.sin((tickCounter + star.phaseOffset) * 0.02f) + 1.0f) * 0.3f + 0.1f;
            int color = (Math.min(255, (int) (alpha * 255)) << 24) | (COL_STAR & 0x00FFFFFF);
            g.fill(star.x, star.y, star.x + 1, star.y + 1, color);
        }

        // 4. 标题与计时器
        drawHeader(g);

        int scrollH = scrollAreaH();

        // 5. 多选提示
        if (multiSelectMode && !hasVoted) {
            Component hint = Component.translatable("vote.multi_select_hint", maxSelect, selectedIndices.size());
            g.drawCenteredString(font, hint, contentX + BUTTON_WIDTH / 2, CONTENT_Y - 14, COL_TEXT_HINT);
        }

        // 6. 选项列表 (裁剪区域使用动态高度)
        g.enableScissor(contentX, CONTENT_Y, contentX + BUTTON_WIDTH, CONTENT_Y + scrollH);
        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            btn.render(g, mouseX, mouseY, drawY, selectedIndices.contains(btn.optionIndex));
            drawY += BUTTON_HEIGHT + BUTTON_SPACING;
        }
        g.disableScissor();

        // 7. 滚动条
        if (maxScroll > 0) {
            drawScrollbar(g, scrollH);
        }

        // 8. 确认按钮 (位置调整，紧贴列表区下方，不再贴底)
        if (multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote()) {
            drawConfirmButton(g, mouseX, mouseY, scrollH);
        }

        // 9. 可重投提示
        if (hasVoted && ClientVoteCache.isAllowReVote()) {
            Component revote = Component.translatable("vote.can_revote").withStyle(ChatFormatting.GREEN);
            g.drawCenteredString(font, revote, width / 2, panelY + panelH - 12, COL_REVOTE);
        }

        // 10. 物品悬停提示
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

    // ── 绘制辅助 ───────────────────────────────────────────────────────────────

    private void drawPanel(GuiGraphics g) {
        int x = panelX, y = panelY, w = panelW, h = panelH;

        // 外阴影
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0x28000000);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x40000814);

        // 边框
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_PANEL_BORDER);

        // 面板渐变填充
        g.fillGradient(x, y, x + w, y + h, COL_PANEL_BG_TOP, COL_PANEL_BG_BOT);

        // 科技网格叠加 (每8像素细线)
        for (int gx = x + 8 - (x % 8); gx < x + w; gx += 8) {
            g.fill(gx, y, gx + 1, y + h, COL_GRID);
        }
        for (int gy = y + 8 - (y % 8); gy < y + h; gy += 8) {
            g.fill(x, gy, x + w, gy + 1, COL_GRID);
        }

        // 顶部高光线
        g.fill(x, y, x + w, y + 1, COL_PANEL_SHINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x181ABCCC);

        // 角落 L 形装饰
        int cs = 8;
        int cc = COL_CORNER_MARK;
        g.fill(x - 1, y - 1, x - 1 + cs, y, cc);
        g.fill(x - 1, y - 1, x, y - 1 + cs, cc);
        g.fill(x + w + 1 - cs, y - 1, x + w + 1, y, cc);
        g.fill(x + w, y - 1, x + w + 1, y - 1 + cs, cc);
        g.fill(x - 1, y + h, x - 1 + cs, y + h + 1, cc);
        g.fill(x - 1, y + h + 1 - cs, x, y + h + 1, cc);
        g.fill(x + w + 1 - cs, y + h, x + w + 1, y + h + 1, cc);
        g.fill(x + w, y + h + 1 - cs, x + w + 1, y + h + 1, cc);
    }

    private void drawHeader(GuiGraphics g) {
        // 标题文字
        g.drawCenteredString(font, title, width / 2, panelY + 14, COL_TITLE);

        // 标题两侧菱形装饰
        int titleW = font.width(title);
        int lineY = panelY + 14 + 6;
        int leftEnd = width / 2 - titleW / 2 - 6;
        int rightSt = width / 2 + titleW / 2 + 6;
        if (leftEnd > panelX + 12) {
            g.fill(panelX + 12, lineY, leftEnd, lineY + 1, COL_DIVIDER);
            g.fill(panelX + 12, lineY - 1, panelX + 12 + 4, lineY + 2, COL_DIVIDER_BRIGHT);
        }
        if (rightSt < panelX + panelW - 12) {
            g.fill(rightSt, lineY, panelX + panelW - 12, lineY + 1, COL_DIVIDER);
            g.fill(panelX + panelW - 12 - 4, lineY - 1, panelX + panelW - 12, lineY + 2, COL_DIVIDER_BRIGHT);
        }

        // 计时器徽章 (增加外发光动画)
        int sec = getRemainingSeconds();
        String timeStr = sec >= 0 ? formatTime(sec) : "PAUSED";
        int timerColor;
        boolean urgent = false;
        if (sec < 0) {
            timerColor = COL_TIMER_PAUSED;
        } else if (sec <= 10) {
            timerColor = (tickCounter % 20 < 10) ? COL_TIMER_URGENT_A : COL_TIMER_URGENT_B;
            urgent = true;
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

        // 外发光效果 (仅紧急时明显)
        if (urgent) {
            int glowAlpha = (Mth.sin(tickCounter * 0.3f) > 0 ? 0x25 : 0x10);
            int glowColor = (glowAlpha << 24) | (COL_TIMER_GLOW & 0x00FFFFFF);
            g.fill(tx - 2, ty - 2, tx + tw + 2, ty + badgeH + 2, glowColor);
        } else if (sec > 0 && sec <= 30) {
            g.fill(tx - 1, ty - 1, tx + tw + 1, ty + badgeH + 1, 0x18FFAA33);
        }

        // 徽章背景
        g.fill(tx, ty, tx + tw, ty + badgeH, COL_TIMER_BADGE_BG);
        // 徽章顶部动态彩色线
        int topLineColor = (timerColor & 0x00FFFFFF) | 0xCC000000;
        g.fill(tx, ty, tx + tw, ty + 1, topLineColor);
        // 文字
        g.drawCenteredString(font, timerComp, width / 2, ty + (badgeH - 8) / 2 + 1, timerColor);

        // 列表区上方分隔线
        int sepY = CONTENT_Y - 6;
        g.fill(panelX + 4, sepY, panelX + panelW - 4, sepY + 1, COL_DIVIDER);
        g.fill(panelX + 4, sepY + 1, panelX + panelW - 4, sepY + 2, 0x20FFFFFF);
    }

    private void drawScrollbar(GuiGraphics g, int scrollH) {
        int sx = contentX + BUTTON_WIDTH + 5;
        int total = buttons.size() * (BUTTON_HEIGHT + BUTTON_SPACING) - BUTTON_SPACING;
        double ratio = (double) scrollH / total;
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (scrollH * ratio));
        int thumbY = CONTENT_Y + (int) ((scrollH - thumbH) * ((double) scrollOffset / maxScroll));

        g.fill(sx, CONTENT_Y, sx + SCROLL_WIDTH, CONTENT_Y + scrollH, COL_SCROLL_TRACK);
        g.fillGradient(sx, thumbY, sx + SCROLL_WIDTH, thumbY + thumbH, COL_SCROLL_TOP, COL_SCROLL_BOT);
        // 左侧高亮边
        g.fill(sx, thumbY, sx + 1, thumbY + thumbH, COL_SCROLL_EDGE);
        // 滑块流动光泽
        int shineX = sx + 1;
        int shineW = SCROLL_WIDTH - 2;
        int shineY = thumbY + (int) ((tickCounter * 0.5f) % thumbH);
        g.fill(shineX, shineY, shineX + shineW, Math.min(thumbY + thumbH, shineY + 2), 0x40FFFFFF);
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, int scrollH) {
        // 定位在列表可视区域正下方 + 间距
        int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
        int by = CONTENT_Y + scrollH + CONFIRM_Y_PADDING; // 不再使用绝对底部
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

        // 脉冲光环效果 (仅可确认时)
        if (canConfirm) {
            float pulse = (Mth.sin(tickCounter * 0.1f) + 1.0f) * 0.5f; // 0-1
            int pulseColor = lerpColor(COL_CONFIRM_PULSE_B, COL_CONFIRM_PULSE_A, pulse);
            g.fill(bx - 1, by - 1, bx + CONFIRM_W + 1, by + CONFIRM_H + 1, pulseColor);
        }

        g.renderOutline(bx, by, CONFIRM_W, CONFIRM_H, borderColor);

        // 顶部动态高光
        if (canConfirm) {
            int shineAlpha = hovered ? 0x60 : 0x30;
            int shineColor = (shineAlpha << 24) | 0x22DD70;
            g.fill(bx + 1, by, bx + CONFIRM_W - 1, by + 1, shineColor);
        }

        int textColor = canConfirm ? COL_TEXT_PRIMARY : COL_TEXT_MUTED;
        Component confirmText = Component.translatable("vote.confirm");
        g.drawCenteredString(font, confirmText, bx + CONFIRM_W / 2, by + (CONFIRM_H - 8) / 2, textColor);
    }

    // 简单颜色插值 (ARGB)
    private int lerpColor(int c1, int c2, float t) {
        int a1 = (c1 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ── 鼠标事件 ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 确认按钮
        if (multiSelectMode && !hasVoted && !ClientVoteCache.isAllowReVote()) {
            int scrollH = scrollAreaH();
            int bx = contentX + (BUTTON_WIDTH - CONFIRM_W) / 2;
            int by = CONTENT_Y + scrollH + CONFIRM_Y_PADDING;
            if (mouseX >= bx && mouseX < bx + CONFIRM_W
                    && mouseY >= by && mouseY < by + CONFIRM_H) {
                if (!selectedIndices.isEmpty())
                    castMultiVote();
                return true;
            }
        }

        // 选项点击
        int drawY = CONTENT_Y - scrollOffset;
        for (WidgetButton btn : buttons) {
            if (btn.mouseClicked(mouseX, mouseY, drawY)) {
                if (multiSelectMode) {
                    if (hasVoted && !ClientVoteCache.isAllowReVote())
                        return true;
                    if (selectedIndices.contains(btn.optionIndex)) {
                        selectedIndices.remove(btn.optionIndex);
                    } else {
                        if (selectedIndices.size() < maxSelect) {
                            selectedIndices.add(btn.optionIndex);
                        } else {
                            this.minecraft.getSoundManager()
                                    .play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0f));
                            return true;
                        }
                    }
                    if (ClientVoteCache.isAllowReVote())
                        castMultiVote();
                    return true;
                } else {
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
            scrollOffset = Mth.clamp(
                    scrollOffset - (int) scrollY * (BUTTON_HEIGHT + BUTTON_SPACING), 0, maxScroll);
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
        this.minecraft.getSoundManager()
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

    // ── 内部辅助类 ─────────────────────────────────────────────────

    private static class Star {
        final int x, y;
        final float phaseOffset;
        final float speed;

        Star(int x, int y, float phaseOffset, float speed) {
            this.x = x;
            this.y = y;
            this.phaseOffset = phaseOffset;
            this.speed = speed;
        }
    }

    // ── WidgetButton 内部类 (增强视觉效果) ────────────────────────────────────
    private class WidgetButton {
        final int optionIndex;

        WidgetButton(int index) {
            this.optionIndex = index;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, int baseY, boolean selected) {
            int x = contentX, y = baseY, w = BUTTON_WIDTH, h = BUTTON_HEIGHT;

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

            // 左侧高亮条
            if (selected) {
                g.fill(x, y, x + 2, y + h, COL_ACCENT_TEAL);
            } else if (hovered) {
                g.fill(x, y, x + 2, y + h, COL_ACCENT_BLUE);
            }

            // 悬停/选中高光
            if (hovered || selected) {
                int shineColor = selected ? 0x301ABCCC : 0x20FFFFFF;
                g.fill(x + 1, y + 1, x + w - 1, y + 2, shineColor);
            }

            // 进度条 (带流动光泽)
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

                    // 流动光泽
                    int shineOffset = (int) ((tickCounter * 0.8f) % (barW + 10)) - 4;
                    int shineStart = Math.max(x + 2, x + 2 + shineOffset);
                    int shineEnd = Math.min(x + 2 + barW, shineStart + 8);
                    if (shineStart < shineEnd) {
                        g.fill(shineStart, y + h - 3, shineEnd, y + h - 1, COL_BAR_SHINE);
                    }
                }

                // 票数
                String voteStr = String.valueOf(votes);
                int voteRight = x + w - 6;
                if (selected)
                    voteRight -= 14;
                g.drawString(font, voteStr, voteRight - font.width(voteStr), y + (h - 8) / 2, COL_TEXT_MUTED);
            }

            // 图标
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

            // 标签文字
            Component display = option.display();
            int textColor = selected ? COL_TEXT_SELECTED : (hovered ? COL_TEXT_HOVER : COL_TEXT_NORMAL);
            if (hasIcon) {
                g.drawString(font, display, iconX + ICON_SIZE + 6, y + (h - 8) / 2, textColor);
            } else {
                g.drawCenteredString(font, display, x + w / 2, y + (h - 8) / 2, textColor);
            }

            // 选中勾号 (带光晕)
            if (selected) {
                g.fill(x + w - 14, y + (h - 8) / 2 - 1, x + w - 2, y + (h - 8) / 2 + 9, COL_CHECK_GLOW);
                g.drawString(font, "✔", x + w - 16, y + (h - 8) / 2, COL_CHECK);
            }
        }

        boolean mouseClicked(double mx, double my, int baseY) {
            return mx >= contentX && mx < contentX + BUTTON_WIDTH
                    && my >= baseY && my < baseY + BUTTON_HEIGHT;
        }
    }
}