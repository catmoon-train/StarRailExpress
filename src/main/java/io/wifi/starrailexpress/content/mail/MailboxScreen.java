package io.wifi.starrailexpress.content.mail;

import io.wifi.starrailexpress.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 邮箱 GUI – 左侧邮件列表 + 右侧邮件内容，半透明玻璃风格，带丰富动画。
 * <p>
 * 布局：左 {@value #LEFT_W}px 邮件列表 / 右侧邮件内容，共享标题栏与底部操作栏。
 */
public class MailboxScreen extends Screen {

    // ── 布局常量 ────────────────────────────────────────────────────────────
    private static final int PANEL_W  = 570;
    private static final int PANEL_H  = 390;
    private static final int LEFT_W   = 210;
    private static final int HDR_H    = 34;
    private static final int FOOTER_H = 42;
    private static final int ROW_H    = 42;
    private static final int ROW_GAP  = 3;
    private static final int ROW_STRIDE = ROW_H + ROW_GAP;

    // ── 颜色（半透明） ───────────────────────────────────────────────────────
    private static final int BG_LEFT        = 0xCC080E1C;
    private static final int BG_RIGHT       = 0xCC0B1220;
    private static final int HDR_LEFT       = 0xEE060B16;
    private static final int HDR_RIGHT      = 0xEE08101E;
    private static final int FTR_BG         = 0xBB060912;
    private static final int DIVIDER_COLOR  = 0x55FFFFFF;
    private static final int ROW_UNREAD     = 0xBB0D2B58;
    private static final int ROW_READ       = 0x881A2038;
    private static final int ROW_CLAIMED    = 0x880C1828;
    private static final int ROW_SEL        = 0xCC163468;
    private static final int ACCENT_GOLD    = 0xFFFFAA00;
    private static final int ACCENT_GREEN   = 0xFF00DD88;
    private static final int ACCENT_RED     = 0xFFCC4444;
    private static final int ACCENT_BLUE    = 0xFF5599EE;
    private static final int TEXT_WHITE     = 0xFFFFFFFF;
    private static final int TEXT_GRAY      = 0xFFAAAAAA;
    private static final int TEXT_DIM       = 0xFF556688;

    // ── 日期格式 ────────────────────────────────────────────────────────────
    private static final SimpleDateFormat DATE_LONG  = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("MM-dd HH:mm");

    // ── 数据 ────────────────────────────────────────────────────────────────
    private final MailboxComponent mailbox;
    private List<Mail> cachedMails = new ArrayList<>();
    private int selectedIdx = -1;
    private int page = 0;

    // ── 布局缓存 ─────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int listAreaH, rowsPerPage;

    // ── 动画状态 ─────────────────────────────────────────────────────────────
    /** 开场淡入计数器：0 → OPEN_TICKS */
    private int openTick = 0;
    private static final int OPEN_TICKS = 14;
    /** 选中高亮淡入计数器：每次选中后重置为 0 → SELECT_TICKS */
    private int selectTick = 0;
    private static final int SELECT_TICKS = 8;
    /** 每个可见行的悬浮动画值 [0, 1]，在 render() 中每帧插值 */
    private float[] rowHoverAnims = new float[0];
    /** 领取反馈悬浮文字的剩余显示帧数（由 tick() 递减） */
    private float claimFeedbackTimer = 0f;
    private String claimFeedbackText  = "";
    private int    claimFeedbackColor = ACCENT_GREEN;

    // =========================================================================
    // 构造
    // =========================================================================

    public MailboxScreen() {
        super(Component.translatable("gui.sre.mailbox.title"));
        LocalPlayer player = Minecraft.getInstance().player;
        this.mailbox = MailboxComponent.KEY.get(player);
    }

    // =========================================================================
    // 布局
    // =========================================================================

    private void computeLayout() {
        panelW = Math.min(PANEL_W, this.width  - 20);
        panelH = Math.min(PANEL_H, this.height - 20);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listAreaH  = panelH - HDR_H - FOOTER_H;
        rowsPerPage = Math.max(1, listAreaH / ROW_STRIDE);
        if (rowHoverAnims.length != rowsPerPage) {
            rowHoverAnims = new float[rowsPerPage];
        }
    }

    /** X 起点：左侧列表区 */
    private int lx() { return panelX; }
    /** X 起点：右侧内容区 */
    private int rx() { return panelX + LEFT_W; }
    /** 右侧内容区宽度 */
    private int rw() { return panelW - LEFT_W; }

    // =========================================================================
    // 初始化
    // =========================================================================

    @Override
    protected void init() {
        clearWidgets();
        computeLayout();
        refreshMails();
        clampSelectionAndPage();
        initButtons();
    }

    private void refreshMails() {
        List<Mail> visible = new ArrayList<>();
        for (Mail m : mailbox.getMails()) {
            if (!m.isExpired()) visible.add(m);
        }
        visible.sort((a, b) -> {
            if (a.read != b.read) return a.read ? 1 : -1;
            return Long.compare(b.sentAt, a.sentAt);
        });
        cachedMails = visible;
    }

    private void clampSelectionAndPage() {
        if (cachedMails.isEmpty()) {
            selectedIdx = -1;
            page = 0;
            return;
        }
        selectedIdx = Mth.clamp(selectedIdx, -1, cachedMails.size() - 1);
        int maxPage = (cachedMails.size() - 1) / rowsPerPage;
        page = Mth.clamp(page, 0, maxPage);
        // 如果选中邮件不在当前页则跳转
        if (selectedIdx >= 0) {
            int selPage = selectedIdx / rowsPerPage;
            if (selPage != page) page = selPage;
        }
    }

    private void initButtons() {
        int fy   = panelY + panelH - FOOTER_H;
        int btnH = 20;
        int btnY = fy + (FOOTER_H - btnH) / 2;

        // ── 左侧底部：[‹] [Claim All] [Del Read] [›] ──────────────────
        int bw1 = 18, bw2 = 68, sp = 3;
        int totalW = bw1 + sp + bw2 + sp + bw2 + sp + bw1;
        int bx = lx() + (LEFT_W - totalW) / 2;

        int maxPage = cachedMails.isEmpty() ? 0 : (cachedMails.size() - 1) / rowsPerPage;

        if (page > 0) {
            addRenderableWidget(
                    ModernButton.builder(Component.literal("\u2039"), btn -> { page--; init(); })
                            .bounds(bx, btnY, bw1, btnH).build());
        }
        bx += bw1 + sp;

        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.claim_all"), btn -> {
                    int count = mailbox.getClaimableCount();
                    NetworkHandler.sendToServer(MailClaimAllC2SPayload.INSTANCE);
                    if (count > 0) {
                        showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed_all", count).getString(), ACCENT_GOLD);
                    }
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(bx, btnY, bw2, btnH)
                        .accentColor(ACCENT_GOLD).build());
        bx += bw2 + sp;

        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.delete_read"), btn -> {
                    NetworkHandler.sendToServer(MailDeleteAllReadC2SPayload.INSTANCE);
                    selectedIdx = -1;
                    init();
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(bx, btnY, bw2, btnH)
                        .accentColor(ACCENT_RED).build());
        bx += bw2 + sp;

        if (page < maxPage) {
            addRenderableWidget(
                    ModernButton.builder(Component.literal("\u203a"), btn -> { page++; init(); })
                            .bounds(bx, btnY, bw1, btnH).build());
        }

        // ── 右侧底部：[Claim] [Delete] ─────────────────────────────────
        Mail sel = getSelectedMail();
        if (sel != null) {
            int rbx = rx() + 8;

            if (sel.hasRewards() && !sel.claimed && !sel.isExpired()) {
                addRenderableWidget(
                        ModernButton.builder(Component.translatable("gui.sre.mailbox.claim"), btn -> {
                            NetworkHandler.sendToServer(new MailClaimC2SPayload(sel.id));
                            sel.claimed = true;
                            sel.read    = true;
                            showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed").getString(), ACCENT_GREEN);
                            init();
                        }).accentBar(ModernButton.AccentSide.TOP)
                                .bounds(rbx, btnY, 80, btnH)
                                .accentColor(ACCENT_GOLD).build());
                rbx += 84;
            }

            if (sel.canDelete()) {
                addRenderableWidget(
                        ModernButton.builder(Component.translatable("gui.sre.mailbox.delete"), btn -> {
                            NetworkHandler.sendToServer(new MailDeleteC2SPayload(sel.id));
                            selectedIdx = -1;
                            init();
                        }).accentBar(ModernButton.AccentSide.TOP)
                                .bounds(rbx, btnY, 80, btnH)
                                .accentColor(ACCENT_RED).build());
            }
        }
    }

    private Mail getSelectedMail() {
        if (selectedIdx >= 0 && selectedIdx < cachedMails.size()) return cachedMails.get(selectedIdx);
        return null;
    }

    // =========================================================================
    // Tick（动画计数器）
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (openTick   < OPEN_TICKS)   openTick++;
        if (selectTick < SELECT_TICKS)  selectTick++;
        if (claimFeedbackTimer > 0)     claimFeedbackTimer--;
    }

    // =========================================================================
    // renderBackground – 绘制半透明面板底层
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float f) {
        super.renderBackground(g, mx, my, f);
        float ease = easeOutCubic((float) openTick / OPEN_TICKS);

        int lx = lx(), rx = rx();

        // 全局加深遮罩
        g.fill(0, 0, this.width, this.height, 0x88000000);

        // 左列表区背景
        g.fill(lx, panelY, rx, panelY + panelH, withAlpha(BG_LEFT, ease));
        // 右内容区背景
        g.fill(rx, panelY, lx + panelW, panelY + panelH, withAlpha(BG_RIGHT, ease));

        // 标题栏（左/右）
        g.fill(lx, panelY, rx, panelY + HDR_H, withAlpha(HDR_LEFT, ease));
        g.fill(rx, panelY, lx + panelW, panelY + HDR_H, withAlpha(HDR_RIGHT, ease));

        // 底部操作栏背景
        int fy = panelY + panelH - FOOTER_H;
        g.fill(lx, fy, lx + panelW, fy + FOOTER_H, withAlpha(FTR_BG, ease));

        // 分隔线
        g.fill(rx - 1, panelY, rx, panelY + panelH, withAlpha(DIVIDER_COLOR, ease));       // 竖向
        g.fill(lx, panelY + HDR_H - 1, lx + panelW, panelY + HDR_H, withAlpha(DIVIDER_COLOR, ease)); // 标题底
        g.fill(lx, fy, lx + panelW, fy + 1, withAlpha(DIVIDER_COLOR, ease));               // 底部顶

        // 面板顶部光晕线
        g.fill(lx, panelY, lx + panelW, panelY + 1, withAlpha(0x66FFFFFF, ease));
    }

    // =========================================================================
    // render – 主渲染入口
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // 更新悬浮动画（需要在 super.render 之前，因为 super 先渲染背景和按钮）
        updateHoverAnims(mx, my);

        super.render(g, mx, my, delta); // renderBackground + widgets

        float ease    = easeOutCubic((float) openTick   / OPEN_TICKS);
        float selEase = easeOutCubic((float) selectTick / SELECT_TICKS);

        renderHeader(g, ease, selEase);
        renderMailList(g, mx, my, ease, selEase);
        renderMailContent(g, mx, my, ease);
        renderFooterInfo(g, ease);
        renderClaimFeedback(g);
    }

    // ── 标题栏 ────────────────────────────────────────────────────────────────

    private void renderHeader(GuiGraphics g, float ease, float selEase) {
        int lx = lx(), rx = rx();
        int titleY = panelY + (HDR_H - 9) / 2;

        // 左侧：标题 + 未读徽章
        g.drawCenteredString(font, Component.translatable("gui.sre.mailbox.title"),
                lx + LEFT_W / 2, titleY, withAlpha(TEXT_WHITE, ease));

        int unread = mailbox.getUnreadCount();
        if (unread > 0) {
            String badge = String.valueOf(unread);
            int bw = font.width(badge) + 6;
            int bx = rx - bw - 6;
            int by = panelY + (HDR_H - 12) / 2;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 13, withAlpha(0xFF801800, ease));
            g.fill(bx, by, bx + bw, by + 12, withAlpha(0xFFAA2200, ease));
            g.drawString(font, badge, bx + 3, by + 2, withAlpha(TEXT_WHITE, ease));
        }

        // 右侧：选中邮件标题 / 提示
        Mail sel = getSelectedMail();
        if (sel != null) {
            String title = sel.title;
            int maxTW = rw() - 80;
            if (font.width(title) > maxTW) title = font.plainSubstrByWidth(title, maxTW - 6) + "\u2026";
            g.drawString(font, title, rx + 8, titleY, withAlpha(TEXT_WHITE, ease));

            // 状态标签（右对齐）
            String tag; int tagColor;
            if (sel.claimed) {
                tag = Component.translatable("gui.sre.mailbox.tag_claimed").getString(); tagColor = ACCENT_GREEN;
            } else if (sel.isExpired()) {
                tag = Component.translatable("gui.sre.mailbox.status_expired").getString(); tagColor = ACCENT_RED;
            } else if (sel.hasRewards()) {
                tag = Component.translatable("gui.sre.mailbox.tag_reward").getString();  tagColor = ACCENT_GOLD;
            } else {
                tag = null; tagColor = 0;
            }
            if (tag != null) {
                g.drawString(font, tag, rx + rw() - font.width(tag) - 8, titleY,
                        withAlpha(tagColor, ease));
            }
        } else {
            g.drawCenteredString(font,
                    Component.translatable("gui.sre.mailbox.select_hint"),
                    rx + rw() / 2, titleY, withAlpha(TEXT_DIM, ease));
        }
    }

    // ── 悬浮动画更新 ─────────────────────────────────────────────────────────

    private void updateHoverAnims(int mx, int my) {
        int lx = lx();
        int listY = panelY + HDR_H;
        int start = page * rowsPerPage;
        int end   = Math.min(start + rowsPerPage, cachedMails.size());
        for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowY   = listY + rowIdx * ROW_STRIDE + 1;
            boolean hov = mx >= lx + 2 && mx < lx + LEFT_W - 2
                       && my >= rowY   && my < rowY + ROW_H;
            if (rowIdx < rowHoverAnims.length) {
                rowHoverAnims[rowIdx] = Mth.lerp(0.28f, rowHoverAnims[rowIdx], hov ? 1f : 0f);
            }
        }
    }

    // ── 左侧邮件列表 ─────────────────────────────────────────────────────────

    private void renderMailList(GuiGraphics g, int mx, int my, float ease, float selEase) {
        int lx    = lx();
        int listY = panelY + HDR_H;

        if (cachedMails.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.sre.mailbox.empty"),
                    lx + LEFT_W / 2, listY + listAreaH / 2 - 4, withAlpha(TEXT_DIM, ease));
            return;
        }

        int start = page * rowsPerPage;
        int end   = Math.min(start + rowsPerPage, cachedMails.size());
        for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowY   = listY + rowIdx * ROW_STRIDE + 1;
            boolean selected = (i == selectedIdx);
            float   hov      = rowIdx < rowHoverAnims.length ? rowHoverAnims[rowIdx] : 0f;
            float   sa       = selected ? selEase : 0f;
            renderMailRow(g, cachedMails.get(i), lx + 2, rowY, LEFT_W - 4, ROW_H, selected, hov, sa, ease);
        }
    }

    private void renderMailRow(GuiGraphics g, Mail mail,
            int x, int y, int w, int h,
            boolean selected, float hoverAnim, float selAnim, float ease) {
        // 行背景（选中时与基础色混合）
        int baseBg = mail.claimed ? ROW_CLAIMED : (mail.read ? ROW_READ : ROW_UNREAD);
        int bg     = selected ? blendARGB(baseBg, ROW_SEL, selAnim) : baseBg;
        g.fill(x, y, x + w, y + h, withAlpha(bg, ease));

        // 悬浮高亮叠层
        if (hoverAnim > 0.01f) {
            g.fill(x, y, x + w, y + h, ((int)(0x28 * hoverAnim) << 24) | 0xFFFFFF);
        }

        // 左侧状态竖条
        if (selected) {
            int ba = (int)(0xCC * selAnim * ease);
            g.fill(x, y, x + 2, y + h, (ba << 24) | (ACCENT_BLUE  & 0xFFFFFF));
            g.fill(x + 2, y, x + w, y + 1, ((int)(0x55 * selAnim * ease) << 24) | (ACCENT_BLUE & 0xFFFFFF));
        } else if (!mail.read) {
            g.fill(x, y, x + 2, y + h, withAlpha(ACCENT_GOLD, ease));
        } else if (mail.claimed) {
            g.fill(x, y, x + 2, y + h, ((int)(0x88 * ease) << 24) | (ACCENT_GREEN & 0xFFFFFF));
        }

        // 标题
        int titleColor = (selected || !mail.read) ? TEXT_WHITE : TEXT_GRAY;
        String title = mail.title;
        int maxTW = w - 18;
        if (font.width(title) > maxTW) title = font.plainSubstrByWidth(title, maxTW - 6) + "\u2026";
        g.drawString(font, title, x + 6, y + 4, withAlpha(titleColor, ease));

        // 发件人
        String sender = mail.sender;
        if (font.width(sender) > w - 18) sender = font.plainSubstrByWidth(sender, w - 24) + "\u2026";
        g.drawString(font, sender, x + 6, y + 15, withAlpha(TEXT_DIM, ease));

        // 时间
        String dateStr = DATE_LONG.format(new Date(mail.sentAt));
        if (font.width(dateStr) > w - 18) dateStr = DATE_SHORT.format(new Date(mail.sentAt));
        g.drawString(font, dateStr, x + 6, y + 27, withAlpha(TEXT_DIM, ease));

        // 附件徽章（右上角）
        if (mail.hasRewards()) {
            String badge = mail.claimed ? "\u2713" : "\u2605";
            int bc       = mail.claimed ? ACCENT_GREEN : ACCENT_GOLD;
            g.drawString(font, badge, x + w - 11, y + 5, withAlpha(bc, ease));
        }
    }

    // ── 右侧邮件内容 ─────────────────────────────────────────────────────────

    private void renderMailContent(GuiGraphics g, int mx, int my, float ease) {
        int rx = rx(), rw = rw();
        int cy = panelY + HDR_H;
        int ch = listAreaH;

        Mail sel = getSelectedMail();
        if (sel == null) {
            g.drawCenteredString(font,
                    Component.translatable("gui.sre.mailbox.no_selection"),
                    rx + rw / 2, cy + ch / 2 - 4, withAlpha(TEXT_DIM, ease));
            return;
        }

        // 发件人 + 时间
        String meta = sel.sender + "   " + DATE_LONG.format(new Date(sel.sentAt));
        g.drawString(font, meta, rx + 8, cy + 6, withAlpha(TEXT_GRAY, ease));

        // 横向分隔线
        g.fill(rx + 6, cy + 18, rx + rw - 6, cy + 19, withAlpha(DIVIDER_COLOR, ease));

        // 正文（自动换行）
        int maxTW  = rw - 18;
        int lineY  = cy + 24;
        int maxBodyY = cy + ch - (sel.attachments.isEmpty() ? 8 : 72);
        for (String line : wrapText(sel.content, maxTW)) {
            if (lineY + 10 > maxBodyY) {
                g.drawString(font, "\u2026", rx + 8, lineY, withAlpha(TEXT_DIM, ease));
                break;
            }
            g.drawString(font, line, rx + 8, lineY, withAlpha(TEXT_WHITE, ease));
            lineY += 11;
        }

        // 附件区
        if (!sel.attachments.isEmpty()) {
            int attachY = cy + ch - 68;
            g.fill(rx + 6, attachY, rx + rw - 6, attachY + 1, withAlpha(DIVIDER_COLOR, ease));
            g.drawString(font, Component.translatable("gui.sre.mailbox.attachments"),
                    rx + 8, attachY + 4, withAlpha(ACCENT_GOLD, ease));

            int itemX = rx + 8, itemY = attachY + 16;
            int maxItems = Math.min(sel.attachments.size(), 12);
            for (int i = 0; i < maxItems; i++) {
                ItemStack stack = sel.attachments.get(i);
                int ix = itemX + (i % 10) * 20;
                int iy = itemY + (i / 10) * 20;
                g.renderItem(stack, ix, iy);
                g.renderItemDecorations(font, stack, ix, iy);
                if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) {
                    g.renderTooltip(font, stack, mx, my);
                }
            }
            if (sel.attachments.size() > 12) {
                g.drawString(font, "+" + (sel.attachments.size() - 12),
                        itemX + 10 * 20, itemY + 4, withAlpha(TEXT_GRAY, ease));
            }
        }
    }

    // ── 底部页码信息 ─────────────────────────────────────────────────────────

    private void renderFooterInfo(GuiGraphics g, float ease) {
        int fy      = panelY + panelH - FOOTER_H;
        int maxPage = cachedMails.isEmpty() ? 0 : (cachedMails.size() - 1) / rowsPerPage;
        // 页码（左侧上方）
        g.drawCenteredString(font, Component.literal((page + 1) + " / " + (maxPage + 1)),
                lx() + LEFT_W / 2, fy + 4, withAlpha(TEXT_DIM, ease));
        // 统计（右侧上方）
        g.drawString(font, Component.translatable("gui.sre.mailbox.stats",
                cachedMails.size(), mailbox.getClaimableCount()), rx() + 8, fy + 4, withAlpha(TEXT_DIM, ease));
    }

    // ── 领取反馈悬浮文字 ──────────────────────────────────────────────────────

    private void renderClaimFeedback(GuiGraphics g) {
        if (claimFeedbackTimer <= 0) return;
        float t   = claimFeedbackTimer / 70f;             // 1 → 0
        float alpha;
        if      (t > 0.85f) alpha = (1f - t) / 0.15f;   // 淡入
        else if (t < 0.25f) alpha = t / 0.25f;           // 淡出
        else                alpha = 1f;

        // 随时间向上飘移
        float offsetY = (1f - t) * 24f;
        int tw = font.width(claimFeedbackText);
        int tx = this.width / 2 - tw / 2;
        int ty = (int)(panelY - 14 - offsetY);

        // 暗色背景胶囊
        int bgAlpha = (int)(alpha * 0xBB);
        g.fill(tx - 7, ty - 4, tx + tw + 7, ty + 14, (bgAlpha << 24) | 0x00020801);
        g.fill(tx - 6, ty - 3, tx + tw + 6, ty + 13, (bgAlpha << 24) | 0x00050F03);

        // 文字光晕（向四方偏移）
        int glowC = ((int)(alpha * 0x3C) << 24) | (claimFeedbackColor & 0xFFFFFF);
        g.drawString(font, claimFeedbackText, tx - 1, ty, glowC);
        g.drawString(font, claimFeedbackText, tx + 1, ty, glowC);
        g.drawString(font, claimFeedbackText, tx, ty - 1, glowC);
        g.drawString(font, claimFeedbackText, tx, ty + 1, glowC);

        // 主文字
        int fgAlpha = (int)(alpha * 255);
        g.drawString(font, claimFeedbackText, tx, ty, (fgAlpha << 24) | (claimFeedbackColor & 0xFFFFFF));
    }

    private void showFeedback(String text, int color) {
        claimFeedbackText  = text;
        claimFeedbackColor = color;
        claimFeedbackTimer = 70f;
    }

    // =========================================================================
    // 交互
    // =========================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            int relX = (int) mx - lx();
            int relY = (int) my - (panelY + HDR_H);
            if (relX >= 2 && relX < LEFT_W - 2 && relY >= 0 && relY < listAreaH) {
                int rowIdx    = relY / ROW_STRIDE;
                int globalIdx = page * rowsPerPage + rowIdx;
                if (rowIdx < rowsPerPage && globalIdx < cachedMails.size()) {
                    selectMail(globalIdx);
                    return true;
                }
            }
        }
        return false;
    }

    private void selectMail(int idx) {
        if (selectedIdx == idx) return;
        selectedIdx = idx;
        selectTick  = 0;
        Mail mail = cachedMails.get(idx);
        if (!mail.read) {
            NetworkHandler.sendToServer(new MailMarkReadC2SPayload(mail.id));
            mail.read = true;
        }
        init();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }   // ESC
        if (!cachedMails.isEmpty()) {
            if (keyCode == 265 && selectedIdx > 0) {                              // ↑
                selectMail(selectedIdx - 1); return true;
            }
            if (keyCode == 264 && selectedIdx < cachedMails.size() - 1) {        // ↓
                selectMail(selectedIdx + 1); return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================================
    // 工具
    // =========================================================================

    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /**
     * 将 ARGB 颜色的 Alpha 通道乘以 ease 系数 [0,1]。
     * 用于让整个界面随 openAnim 统一淡入。
     */
    private static int withAlpha(int argb, float ease) {
        int a = (int)(((argb >> 24) & 0xFF) * ease);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * 在两个 ARGB 颜色之间按 t [0,1] 线性插值（四通道均插值）。
     */
    private static int blendARGB(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int)(a1 + (a2 - a1) * t) << 24)
             | ((int)(r1 + (r2 - r1) * t) << 16)
             | ((int)(g1 + (g2 - g1) * t) <<  8)
             |  (int)(b1 + (b2 - b1) * t);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String paragraph : text.split("\n")) {
            if (paragraph.isEmpty()) { lines.add(""); continue; }
            StringBuilder cur = new StringBuilder();
            int curW = 0;
            for (char c : paragraph.toCharArray()) {
                int cw = font.width(String.valueOf(c));
                if (curW + cw > maxWidth && !cur.isEmpty()) {
                    lines.add(cur.toString());
                    cur  = new StringBuilder();
                    curW = 0;
                }
                cur.append(c);
                curW += cw;
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
        }
        return lines;
    }
}
