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
 * 邮箱 GUI — 左侧邮件列表 + 右侧邮件内容，复古列车车票风格。
 * <p>
 * 遵循 {@code docs/ui_style.md}：深棕渐变背景 + 棕褐描边 + 浅米色装饰线，
 * 金色强调，奶油色文字，响应式布局，scissor 裁剪滚动区域。
 *
 * <p>布局：左 30% 邮件列表 / 右侧邮件内容，共享标题栏与底部操作栏。
 */
public class MailboxScreen extends Screen {

    // ── 颜色（复古列车车票风格，参见 docs/ui_style.md §2）───────────────────────
    private static final int BG_TOP        = 0xD81A1008;
    private static final int BG_BOTTOM     = 0xD820140A;
    private static final int BORDER        = 0xFF8B6914;
    private static final int DECOR_LINE    = 0x33FFE8C0;
    private static final int GOLD          = 0xFFD4AF37;
    private static final int TEXT          = 0xFFFFF4DC;
    private static final int MUTED         = 0xFF9E8B6E;
    private static final int BODY          = 0xFFC8B898;
    private static final int BLUE          = 0xFF5EB7D8;
    private static final int GREEN         = 0xFF72C17B;
    private static final int RED           = 0xFFE06B65;
    private static final int HOVER_BG      = 0x22FFFFFF;
    private static final int DIVIDER_LINE  = 0x20FFFFFF;
    private static final int HEADER_BG     = 0xCC1A1008;
    private static final int FOOTER_BG     = 0xBB120A04;

    // ── 行背景色 ──────────────────────────────────────────────────────────────
    /** blendColors(0xFF1A1008, 0xFFC9A84C, 0.32F) */
    private static final int ROW_SEL    = 0xFF3E3022;
    private static final int ROW_UNREAD = 0xBB2A1C10;
    private static final int ROW_READ   = 0x881A1008;
    private static final int ROW_CLAIMED= 0x88140C06;

    // ── 布局常量 ──────────────────────────────────────────────────────────────
    private static final int HDR_H      = 34;
    private static final int FOOTER_H   = 42;
    private static final int ROW_H      = 42;
    private static final int ROW_GAP    = 4;
    private static final int ROW_STRIDE = ROW_H + ROW_GAP;

    // ── 尺寸限制 ──────────────────────────────────────────────────────────────
    private static final int MAX_PANEL_W = 700;
    private static final int MAX_PANEL_H = 450;
    private static final int MIN_PANEL_H = 320;

    // ── 日期格式 ──────────────────────────────────────────────────────────────
    private static final SimpleDateFormat DATE_LONG  = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private static final SimpleDateFormat DATE_SHORT = new SimpleDateFormat("MM-dd HH:mm");

    // ── 数据 ──────────────────────────────────────────────────────────────────
    private final MailboxComponent mailbox;
    private List<Mail> cachedMails = new ArrayList<>();
    private int selectedIdx = -1;
    private int page = 0;

    // ── 布局缓存 ──────────────────────────────────────────────────────────────
    private int panelX, panelY, panelW, panelH;
    private int leftW;
    private int listAreaH, rowsPerPage;

    // ── 动画状态 ──────────────────────────────────────────────────────────────
    private int   openTick = 0;
    private static final int OPEN_TICKS   = 14;
    private int   selectTick = 0;
    private static final int SELECT_TICKS = 8;
    private float[] rowHoverAnims = new float[0];
    private float claimFeedbackTimer = 0f;
    private String claimFeedbackText  = "";
    private int    claimFeedbackColor = GREEN;

    // =========================================================================
    // 构造
    // =========================================================================

    public MailboxScreen() {
        super(Component.translatable("gui.sre.mailbox.title"));
        LocalPlayer player = Minecraft.getInstance().player;
        this.mailbox = MailboxComponent.KEY.get(player);
    }

    // =========================================================================
    // 佈局（響應式）
    // =========================================================================

    private void computeLayout() {
        panelW = Math.min(MAX_PANEL_W, (int)(this.width  * 0.9F));
        panelH = Mth.clamp((int)(this.height * 0.85F), MIN_PANEL_H, MAX_PANEL_H);
        panelX = (this.width  - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        leftW   = (int)(panelW * 0.3F);
        listAreaH  = panelH - HDR_H - FOOTER_H;
        rowsPerPage = Math.max(1, listAreaH / ROW_STRIDE);
        if (rowHoverAnims.length != rowsPerPage) {
            rowHoverAnims = new float[rowsPerPage];
        }
    }

    private int lx() { return panelX; }
    private int rx() { return panelX + leftW; }
    private int rw() { return panelW - leftW; }

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
        if (selectedIdx >= 0) {
            int selPage = selectedIdx / rowsPerPage;
            if (selPage != page) page = selPage;
        }
    }

    private void initButtons() {
        int fy   = panelY + panelH - FOOTER_H;
        int btnH = 20;
        int btnY = fy + (FOOTER_H - btnH) / 2;

        int bw1 = 18, bw2 = 68, sp = 3;
        int totalW = bw1 + sp + bw2 + sp + bw2 + sp + bw1;
        int bx = lx() + (leftW - totalW) / 2;

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
                        showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed_all", count).getString(), GOLD);
                    }
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(bx, btnY, bw2, btnH)
                        .accentColor(GOLD).build());
        bx += bw2 + sp;

        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.delete_read"), btn -> {
                    NetworkHandler.sendToServer(MailDeleteAllReadC2SPayload.INSTANCE);
                    selectedIdx = -1;
                    init();
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(bx, btnY, bw2, btnH)
                        .accentColor(RED).build());
        bx += bw2 + sp;

        if (page < maxPage) {
            addRenderableWidget(
                    ModernButton.builder(Component.literal("\u203a"), btn -> { page++; init(); })
                            .bounds(bx, btnY, bw1, btnH).build());
        }

        // 右侧底部
        Mail sel = getSelectedMail();
        if (sel != null) {
            int rbx = rx() + 8;
            if (sel.hasRewards() && !sel.claimed && !sel.isExpired()) {
                addRenderableWidget(
                        ModernButton.builder(Component.translatable("gui.sre.mailbox.claim"), btn -> {
                            NetworkHandler.sendToServer(new MailClaimC2SPayload(sel.id));
                            sel.claimed = true;
                            sel.read    = true;
                            showFeedback(Component.translatable("gui.sre.mailbox.feedback_claimed").getString(), GREEN);
                            init();
                        }).accentBar(ModernButton.AccentSide.TOP)
                                .bounds(rbx, btnY, 80, btnH)
                                .accentColor(GOLD).build());
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
                                .accentColor(RED).build());
            }
        }
    }

    private Mail getSelectedMail() {
        if (selectedIdx >= 0 && selectedIdx < cachedMails.size()) return cachedMails.get(selectedIdx);
        return null;
    }

    // =========================================================================
    // Tick
    // =========================================================================

    @Override
    public void tick() {
        super.tick();
        if (openTick   < OPEN_TICKS)   openTick++;
        if (selectTick < SELECT_TICKS)  selectTick++;
        if (claimFeedbackTimer > 0)     claimFeedbackTimer--;
    }

    // =========================================================================
    // renderBackground — 复古列车车票面板（渐变 + 描边 + 装饰线）
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float f) {
        super.renderBackground(g, mx, my, f);
        float ease = easeOutCubic((float) openTick / OPEN_TICKS);

        int px = panelX, py = panelY, pw = panelW, ph = panelH;
        int lw = leftW;

        // 全局加深遮罩
        g.fill(0, 0, this.width, this.height, ((int)(0x88 * ease) << 24));

        // 面板上下渐变背景
        g.fillGradient(px, py, px + pw, py + ph,
                withAlpha(BG_TOP, ease), withAlpha(BG_BOTTOM, ease));

        // 棕褐色描边
        g.renderOutline(px, py, pw, ph, withAlpha(BORDER, ease));

        // 上边缘装饰线（内侧 1px）
        g.fill(px + 1, py + 1, px + pw - 1, py + 2, withAlpha(DECOR_LINE, ease));

        // 左列表区 / 右内容区分隔竖线
        g.fill(px + lw - 1, py + 1, px + lw, py + ph - 1, withAlpha(DIVIDER_LINE, ease));

        // 标题栏底分隔
        int hy = py + HDR_H;
        g.fill(px + 1, hy - 1, px + pw - 1, hy, withAlpha(DIVIDER_LINE, ease));

        // 底部操作栏顶分隔
        int fy = py + ph - FOOTER_H;
        g.fill(px + 1, fy, px + pw - 1, fy + 1, withAlpha(DIVIDER_LINE, ease));

        // 标题栏与底部栏加深底色
        g.fill(px + 1, py + 2, px + pw - 1, py + HDR_H, withAlpha(HEADER_BG, ease));
        g.fill(px + 1, fy + 1, px + pw - 1, fy + FOOTER_H, withAlpha(FOOTER_BG, ease));
    }

    // =========================================================================
    // render — 主渲染入口
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        updateHoverAnims(mx, my);
        super.render(g, mx, my, delta);

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

        // 左侧：标题（奶油色）
        g.drawCenteredString(font, Component.translatable("gui.sre.mailbox.title"),
                lx + leftW / 2, titleY, withAlpha(TEXT, ease));

        // 未读徽章
        int unread = mailbox.getUnreadCount();
        if (unread > 0) {
            String badge = String.valueOf(unread);
            int bw = font.width(badge) + 6;
            int bx = rx - bw - 6;
            int by = panelY + (HDR_H - 12) / 2;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 13, withAlpha(GOLD, ease));
            g.fill(bx, by, bx + bw, by + 12, withAlpha(0xFF1A1008, ease));
            g.drawString(font, badge, bx + 3, by + 2, withAlpha(TEXT, ease));
        }

        // 右侧：选中邮件标题 / 提示
        Mail sel = getSelectedMail();
        if (sel != null) {
            String title = sel.title;
            int maxTW = rw() - 80;
            if (font.width(title) > maxTW) title = font.plainSubstrByWidth(title, maxTW - 6) + "\u2026";
            g.drawString(font, title, rx + 8, titleY, withAlpha(TEXT, ease));

            String tag; int tagColor;
            if (sel.claimed) {
                tag = Component.translatable("gui.sre.mailbox.tag_claimed").getString(); tagColor = GREEN;
            } else if (sel.isExpired()) {
                tag = Component.translatable("gui.sre.mailbox.status_expired").getString(); tagColor = RED;
            } else if (sel.hasRewards()) {
                tag = Component.translatable("gui.sre.mailbox.tag_reward").getString();  tagColor = GOLD;
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
                    rx + rw() / 2, titleY, withAlpha(MUTED, ease));
        }
    }

    // ── 悬浮动画更新（每帧插值）─────────────────────────────────────────────────

    private void updateHoverAnims(int mx, int my) {
        int lx = lx();
        int listY = panelY + HDR_H;
        int start = page * rowsPerPage;
        int end   = Math.min(start + rowsPerPage, cachedMails.size());
        for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowY   = listY + rowIdx * ROW_STRIDE + 1;
            boolean hov = mx >= lx + 2 && mx < lx + leftW - 2
                       && my >= rowY   && my < rowY + ROW_H;
            if (rowIdx < rowHoverAnims.length) {
                rowHoverAnims[rowIdx] = Mth.lerp(0.22F, rowHoverAnims[rowIdx], hov ? 1F : 0F);
            }
        }
    }

    // ── 左侧邮件列表（scissor 裁剪）────────────────────────────────────────────

    private void renderMailList(GuiGraphics g, int mx, int my, float ease, float selEase) {
        int lx    = lx();
        int listY = panelY + HDR_H;

        if (cachedMails.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.sre.mailbox.empty"),
                    lx + leftW / 2, listY + listAreaH / 2 - 4, withAlpha(MUTED, ease));
            return;
        }

        g.enableScissor(lx + 1, listY, lx + leftW - 1, listY + listAreaH);

        int start = page * rowsPerPage;
        int end   = Math.min(start + rowsPerPage, cachedMails.size());
        for (int i = start; i < end; i++) {
            int rowIdx = i - start;
            int rowY   = listY + rowIdx * ROW_STRIDE + 1;
            boolean selected = (i == selectedIdx);
            float   hov      = rowIdx < rowHoverAnims.length ? rowHoverAnims[rowIdx] : 0F;
            float   sa       = selected ? selEase : 0F;
            renderMailRow(g, cachedMails.get(i), lx + 2, rowY, leftW - 4, ROW_H,
                    selected, hov, sa, ease);
        }

        g.disableScissor();
    }

    private void renderMailRow(GuiGraphics g, Mail mail,
            int x, int y, int w, int h,
            boolean selected, float hoverAnim, float selAnim, float ease) {

        // 行背景：选中时与 SELECTED_BG 混合
        int baseBg = mail.claimed ? ROW_CLAIMED : (mail.read ? ROW_READ : ROW_UNREAD);
        int bg = selected ? blendColors(baseBg, ROW_SEL, selAnim) : baseBg;
        g.fill(x, y, x + w, y + h, withAlpha(bg, ease));

        // 悬浮高亮叠层（22% 白）
        if (hoverAnim > 0.01F) {
            g.fill(x, y, x + w, y + h, withAlpha(HOVER_BG, hoverAnim * ease));
        }

        // 行底分隔线
        g.fill(x + 4, y + h - 1, x + w - 4, y + h, withAlpha(DIVIDER_LINE, ease));

        // 左侧状态竖条
        if (selected) {
            int ba = (int)(0xCC * selAnim * ease);
            g.fill(x, y, x + 2, y + h, (ba << 24) | (GOLD & 0x00FFFFFF));
        } else if (!mail.read) {
            g.fill(x, y, x + 2, y + h, withAlpha(0xBBC9A84C, ease));
        } else if (mail.claimed) {
            g.fill(x, y, x + 2, y + h, ((int)(0x88 * ease) << 24) | (GREEN & 0x00FFFFFF));
        }

        // 标题
        int titleColor = (selected || !mail.read) ? TEXT : MUTED;
        String title = mail.title;
        int maxTW = w - 18;
        if (font.width(title) > maxTW) title = font.plainSubstrByWidth(title, maxTW - 6) + "\u2026";
        g.drawString(font, title, x + 6, y + 4, withAlpha(titleColor, ease));

        // 发件人
        String sender = mail.sender;
        if (font.width(sender) > w - 18) sender = font.plainSubstrByWidth(sender, w - 24) + "\u2026";
        g.drawString(font, sender, x + 6, y + 15, withAlpha(MUTED, ease));

        // 时间
        String dateStr = DATE_LONG.format(new Date(mail.sentAt));
        if (font.width(dateStr) > w - 18) dateStr = DATE_SHORT.format(new Date(mail.sentAt));
        g.drawString(font, dateStr, x + 6, y + 27, withAlpha(MUTED, ease));

        // 附件徽章（右上角）
        if (mail.hasRewards()) {
            String badge = mail.claimed ? "\u2713" : "\u2605";
            int bc       = mail.claimed ? GREEN : GOLD;
            g.drawString(font, badge, x + w - 11, y + 5, withAlpha(bc, ease));
        }
    }

    // ── 右侧邮件内容 ──────────────────────────────────────────────────────────

    private void renderMailContent(GuiGraphics g, int mx, int my, float ease) {
        int rx = rx(), rw = rw();
        int cy = panelY + HDR_H;
        int ch = listAreaH;

        Mail sel = getSelectedMail();
        if (sel == null) {
            g.drawCenteredString(font,
                    Component.translatable("gui.sre.mailbox.no_selection"),
                    rx + rw / 2, cy + ch / 2 - 4, withAlpha(MUTED, ease));
            return;
        }

        g.enableScissor(rx + 1, cy, rx + rw - 1, cy + ch);

        // 发件人 + 时间
        String meta = sel.sender + "   " + DATE_LONG.format(new Date(sel.sentAt));
        g.drawString(font, meta, rx + 8, cy + 6, withAlpha(MUTED, ease));

        // 横向分隔
        g.fill(rx + 6, cy + 18, rx + rw - 6, cy + 19, withAlpha(DIVIDER_LINE, ease));

        // 正文
        int maxTW  = rw - 18;
        int lineY  = cy + 24;
        int maxBodyY = cy + ch - (sel.attachments.isEmpty() ? 8 : 72);
        for (String line : wrapText(sel.content, maxTW)) {
            if (lineY + 10 > maxBodyY) {
                g.drawString(font, "\u2026", rx + 8, lineY, withAlpha(MUTED, ease));
                break;
            }
            g.drawString(font, line, rx + 8, lineY, withAlpha(BODY, ease));
            lineY += 11;
        }

        // 附件区
        if (!sel.attachments.isEmpty()) {
            int attachY = cy + ch - 68;
            g.fill(rx + 6, attachY, rx + rw - 6, attachY + 1, withAlpha(DIVIDER_LINE, ease));
            g.drawString(font, Component.translatable("gui.sre.mailbox.attachments"),
                    rx + 8, attachY + 4, withAlpha(GOLD, ease));

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
                        itemX + 10 * 20, itemY + 4, withAlpha(MUTED, ease));
            }
        }

        g.disableScissor();
    }

    // ── 底部页码信息 ──────────────────────────────────────────────────────────

    private void renderFooterInfo(GuiGraphics g, float ease) {
        int fy      = panelY + panelH - FOOTER_H;
        int maxPage = cachedMails.isEmpty() ? 0 : (cachedMails.size() - 1) / rowsPerPage;
        g.drawCenteredString(font, Component.literal((page + 1) + " / " + (maxPage + 1)),
                lx() + leftW / 2, fy + 4, withAlpha(MUTED, ease));
        g.drawString(font, Component.translatable("gui.sre.mailbox.stats",
                cachedMails.size(), mailbox.getClaimableCount()), rx() + 8, fy + 4, withAlpha(MUTED, ease));
    }

    // ── 领取反馈悬浮文字 ──────────────────────────────────────────────────────

    private void renderClaimFeedback(GuiGraphics g) {
        if (claimFeedbackTimer <= 0) return;
        float t   = claimFeedbackTimer / 70F;
        float alpha;
        if      (t > 0.85F) alpha = (1F - t) / 0.15F;
        else if (t < 0.25F) alpha = t / 0.25F;
        else                alpha = 1F;

        float offsetY = (1F - t) * 24F;
        int tw = font.width(claimFeedbackText);
        int tx = this.width / 2 - tw / 2;
        int ty = (int)(panelY - 14 - offsetY);

        int bgAlpha = (int)(alpha * 0xBB);
        g.fill(tx - 7, ty - 4, tx + tw + 7, ty + 14, (bgAlpha << 24) | 0x00100804);
        g.fill(tx - 6, ty - 3, tx + tw + 6, ty + 13, (bgAlpha << 24) | 0x001A1008);

        // 光晕
        int glowC = ((int)(alpha * 0x3C) << 24) | (claimFeedbackColor & 0x00FFFFFF);
        g.drawString(font, claimFeedbackText, tx - 1, ty, glowC);
        g.drawString(font, claimFeedbackText, tx + 1, ty, glowC);
        g.drawString(font, claimFeedbackText, tx, ty - 1, glowC);
        g.drawString(font, claimFeedbackText, tx, ty + 1, glowC);

        int fgAlpha = (int)(alpha * 255);
        g.drawString(font, claimFeedbackText, tx, ty, (fgAlpha << 24) | (claimFeedbackColor & 0x00FFFFFF));
    }

    private void showFeedback(String text, int color) {
        claimFeedbackText  = text;
        claimFeedbackColor = color;
        claimFeedbackTimer = 70F;
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
            if (relX >= 2 && relX < leftW - 2 && relY >= 0 && relY < listAreaH) {
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
        if (keyCode == 256) { onClose(); return true; }
        if (!cachedMails.isEmpty()) {
            if (keyCode == 265 && selectedIdx > 0) {
                selectMail(selectedIdx - 1); return true;
            }
            if (keyCode == 264 && selectedIdx < cachedMails.size() - 1) {
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

    /** easeOutCubic，参见 docs/ui_style.md §6 */
    private static float easeOutCubic(float t) {
        t = Mth.clamp(t, 0F, 1F);
        return 1F - (1F - t) * (1F - t) * (1F - t);
    }

    /** 将 ARGB 颜色的 Alpha 通道乘以 ease 系数 */
    private static int withAlpha(int argb, float ease) {
        int a = (int)(((argb >> 24) & 0xFF) * ease);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** 四通道 ARGB 线性插值，参见 docs/ui_style.md §6 */
    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0F) return c1;
        if (t >= 1F) return c2;
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
