package io.wifi.starrailexpress.mail;

import io.wifi.starrailexpress.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 邮箱 GUI – 显示邮件列表，支持领取、删除和查看附件。
 */
public class MailboxScreen extends Screen {

    // ------- 布局常量 -------
    private static final int ROW_H = 40;
    private static final int ROW_STRIDE = ROW_H + 4;
    private static final int HDR_H = 40;
    private static final int BTN_AREA_H = 30;
    private static final int FOOTER_H = 36;

    // ------- 颜色 -------
    private static final int BG_COLOR = 0xCC1A1A2E;
    private static final int HEADER_COLOR = 0xDD16213E;
    private static final int ROW_UNREAD = 0xBB0F3460;
    private static final int ROW_READ = 0xBB1A1A2E;
    private static final int ROW_CLAIMED = 0xBB0D1B2A;
    private static final int ROW_HOVER = 0x33FFFFFF;
    private static final int ACCENT_GOLD = 0xFFFFAA00;
    private static final int ACCENT_GREEN = 0xFF00CC88;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int TEXT_DIM = 0xFF666666;

    // ------- 标题截断边距 -------
    private static final int TITLE_RIGHT_MARGIN = 80;
    private static final int TITLE_TRUNCATE_MARGIN = 88;
    private final MailboxComponent mailbox;
    private int page = 0;

    // ------- 布局缓存 -------
    private int panelX, panelY, panelW, panelH;
    private int listAreaY, listAreaH, rowsPerPage;

    // ------- 详情模式 -------
    private Mail selectedMail = null;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public MailboxScreen() {
        super(Component.translatable("gui.sre.mailbox.title"));
        LocalPlayer player = Minecraft.getInstance().player;
        this.mailbox = MailboxComponent.KEY.get(player);
    }

    // =========================================================================
    // 布局
    // =========================================================================

    private void computeLayout() {
        panelW = Math.min(360, this.width - 16);
        panelH = Math.min(400, this.height - 16);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        listAreaY = panelY + HDR_H + BTN_AREA_H;
        listAreaH = Math.max(ROW_STRIDE, panelH - HDR_H - BTN_AREA_H - FOOTER_H);
        rowsPerPage = Math.max(1, listAreaH / ROW_STRIDE);
    }

    // =========================================================================
    // 初始化
    // =========================================================================

    @Override
    protected void init() {
        clearWidgets();
        computeLayout();

        List<Mail> mails = getVisibleMails();
        int maxPage = Math.max(0, (mails.size() - 1) / rowsPerPage);
        page = Math.min(page, maxPage);

        if (selectedMail != null) {
            initDetailView();
        } else {
            initListView();
        }
    }

    private void initListView() {
        int btnY = panelY + HDR_H + 4;
        int btnW = Math.min(100, (panelW - 32) / 3);

        // 一键领取
        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.claim_all"), btn -> {
                    NetworkHandler.sendToServer(MailClaimAllC2SPayload.INSTANCE);
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(panelX + 8, btnY, btnW, 20)
                        .accentColor(ACCENT_GOLD)
                        .build());

        // 删除已读
        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.delete_read"), btn -> {
                    NetworkHandler.sendToServer(MailDeleteAllReadC2SPayload.INSTANCE);
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(panelX + 8 + btnW + 8, btnY, btnW, 20)
                        .accentColor(0xFFCC4444)
                        .build());

        // 分页
        List<Mail> mails = getVisibleMails();
        int maxPage = Math.max(0, (mails.size() - 1) / rowsPerPage);
        int pgY = panelY + panelH - FOOTER_H + 4;

        if (page > 0) {
            addRenderableWidget(
                    ModernButton.builder(Component.literal("<"), btn -> {
                        page--;
                        init();
                    }).bounds(panelX + 8, pgY, 20, 20).build());
        }
        if (page < maxPage) {
            addRenderableWidget(
                    ModernButton.builder(Component.literal(">"), btn -> {
                        page++;
                        init();
                    }).bounds(panelX + panelW - 28, pgY, 20, 20).build());
        }
    }

    private void initDetailView() {
        int btnY = panelY + panelH - FOOTER_H + 4;
        int btnW = Math.min(100, (panelW - 40) / 3);

        // 返回按钮
        addRenderableWidget(
                ModernButton.builder(Component.translatable("gui.sre.mailbox.back"), btn -> {
                    selectedMail = null;
                    init();
                }).bounds(panelX + 8, btnY, btnW, 20).build());

        // 领取按钮
        if (selectedMail.hasRewards() && !selectedMail.claimed && !selectedMail.isExpired()) {
            addRenderableWidget(
                    ModernButton.builder(Component.translatable("gui.sre.mailbox.claim"), btn -> {
                        NetworkHandler.sendToServer(new MailClaimC2SPayload(selectedMail.id));
                        selectedMail.claimed = true; // 乐观更新
                        init();
                    }).accentBar(ModernButton.AccentSide.TOP)
                            .bounds(panelX + 8 + btnW + 8, btnY, btnW, 20)
                            .accentColor(ACCENT_GOLD)
                            .build());
        }

        // 删除按钮
        if (selectedMail.canDelete()) {
            addRenderableWidget(
                    ModernButton.builder(Component.translatable("gui.sre.mailbox.delete"), btn -> {
                        NetworkHandler.sendToServer(new MailDeleteC2SPayload(selectedMail.id));
                        selectedMail = null;
                        init();
                    }).accentBar(ModernButton.AccentSide.TOP)
                            .bounds(panelX + 8 + (btnW + 8) * 2, btnY, btnW, 20)
                            .accentColor(0xFFCC4444)
                            .build());
        }
    }

    // =========================================================================
    // 渲染
    // =========================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 暗化背景
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
        // 面板
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, BG_COLOR);
        // 标题栏
        graphics.fill(panelX, panelY, panelX + panelW, panelY + HDR_H, HEADER_COLOR);

        if (selectedMail != null) {
            renderDetailView(graphics, mouseX, mouseY);
        } else {
            renderListView(graphics, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    private void renderListView(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题
        int unread = mailbox.getUnreadCount();
        Component title = Component.translatable("gui.sre.mailbox.title");
        graphics.drawCenteredString(font, title, panelX + panelW / 2, panelY + 8, TEXT_WHITE);
        if (unread > 0) {
            Component badge = Component.translatable("gui.sre.mailbox.unread", unread);
            graphics.drawString(font, badge, panelX + panelW - font.width(badge) - 8, panelY + 8, ACCENT_GOLD);
        }

        // 统计
        List<Mail> mails = getVisibleMails();
        Component stats = Component.translatable("gui.sre.mailbox.stats",
                mails.size(), mailbox.getClaimableCount());
        graphics.drawCenteredString(font, stats, panelX + panelW / 2, panelY + 24, TEXT_GRAY);

        // 邮件列表
        int start = page * rowsPerPage;
        int end = Math.min(start + rowsPerPage, mails.size());
        for (int i = start; i < end; i++) {
            Mail mail = mails.get(i);
            int rowY = listAreaY + (i - start) * ROW_STRIDE;
            renderMailRow(graphics, mail, panelX + 4, rowY, panelW - 8, ROW_H, mouseX, mouseY);
        }

        // 空邮箱提示
        if (mails.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.sre.mailbox.empty"),
                    panelX + panelW / 2, listAreaY + listAreaH / 2 - 4, TEXT_DIM);
        }

        // 页码
        int maxPage = Math.max(0, (mails.size() - 1) / rowsPerPage);
        int pgY = panelY + panelH - FOOTER_H + 8;
        graphics.drawCenteredString(font,
                Component.literal((page + 1) + " / " + (maxPage + 1)),
                panelX + panelW / 2, pgY, TEXT_GRAY);
    }

    private void renderMailRow(GuiGraphics graphics, Mail mail, int x, int y, int w, int h,
                               int mouseX, int mouseY) {
        // 背景颜色
        int bgColor = mail.claimed ? ROW_CLAIMED : (mail.read ? ROW_READ : ROW_UNREAD);
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Hover 高亮
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
            graphics.fill(x, y, x + w, y + h, ROW_HOVER);
        }

        // 未读指示器
        if (!mail.read) {
            graphics.fill(x, y, x + 3, y + h, ACCENT_GOLD);
        }

        // 标题
        int textX = x + 8;
        String displayTitle = mail.title;
        if (font.width(displayTitle) > w - TITLE_RIGHT_MARGIN) {
            displayTitle = font.plainSubstrByWidth(displayTitle, w - TITLE_TRUNCATE_MARGIN) + "...";
        }
        graphics.drawString(font, displayTitle, textX, y + 4, mail.read ? TEXT_GRAY : TEXT_WHITE);

        // 发送者 + 时间
        String dateStr = DATE_FORMAT.format(new Date(mail.sentAt));
        String info = mail.sender + "  " + dateStr;
        graphics.drawString(font, info, textX, y + 16, TEXT_DIM);

        // 附件指示器
        if (mail.hasRewards()) {
            int badgeX = x + w - 50;
            if (mail.claimed) {
                graphics.drawString(font,
                        Component.translatable("gui.sre.mailbox.claimed_badge"),
                        badgeX, y + 4, ACCENT_GREEN);
            } else {
                graphics.drawString(font,
                        Component.translatable("gui.sre.mailbox.reward_badge"),
                        badgeX, y + 4, ACCENT_GOLD);
            }
        }

        // 附件物品预览（最多显示 4 个）
        if (!mail.attachments.isEmpty()) {
            int itemX = textX;
            int itemY = y + 26;
            int count = Math.min(4, mail.attachments.size());
            for (int i = 0; i < count; i++) {
                renderSmallItem(graphics, mail.attachments.get(i), itemX + i * 18, itemY);
            }
            if (mail.attachments.size() > 4) {
                graphics.drawString(font, "+" + (mail.attachments.size() - 4),
                        itemX + 4 * 18, itemY + 4, TEXT_GRAY);
            }
        }
    }

    private void renderDetailView(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题
        graphics.drawCenteredString(font, selectedMail.title,
                panelX + panelW / 2, panelY + 8, TEXT_WHITE);
        // 发送者 + 时间
        String dateStr = DATE_FORMAT.format(new Date(selectedMail.sentAt));
        graphics.drawString(font, selectedMail.sender + "  " + dateStr,
                panelX + 8, panelY + 24, TEXT_GRAY);

        // 分隔线
        int contentY = panelY + HDR_H + 8;
        graphics.fill(panelX + 8, contentY - 1, panelX + panelW - 8, contentY, 0x44FFFFFF);

        // 正文（自动换行）
        int maxTextWidth = panelW - 24;
        List<String> lines = wrapText(selectedMail.content, maxTextWidth);
        int lineY = contentY + 4;
        for (String line : lines) {
            if (lineY + 10 > panelY + panelH - FOOTER_H - 60) break;
            graphics.drawString(font, line, panelX + 12, lineY, TEXT_WHITE);
            lineY += 11;
        }

        // 附件展示
        if (!selectedMail.attachments.isEmpty()) {
            int attachY = Math.min(lineY + 12, panelY + panelH - FOOTER_H - 48);
            graphics.fill(panelX + 8, attachY - 1, panelX + panelW - 8, attachY, 0x44FFFFFF);
            graphics.drawString(font,
                    Component.translatable("gui.sre.mailbox.attachments"),
                    panelX + 12, attachY + 4, ACCENT_GOLD);

            int itemX = panelX + 12;
            int itemY = attachY + 18;
            for (int i = 0; i < selectedMail.attachments.size(); i++) {
                ItemStack stack = selectedMail.attachments.get(i);
                int ix = itemX + (i % 8) * 20;
                int iy = itemY + (i / 8) * 20;
                graphics.renderItem(stack, ix, iy);
                graphics.renderItemDecorations(font, stack, ix, iy);

                // Tooltip
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= iy && mouseY < iy + 16) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }

        // 状态标签
        if (selectedMail.claimed) {
            graphics.drawString(font,
                    Component.translatable("gui.sre.mailbox.status_claimed"),
                    panelX + panelW - 70, panelY + HDR_H + 4, ACCENT_GREEN);
        } else if (selectedMail.isExpired()) {
            graphics.drawString(font,
                    Component.translatable("gui.sre.mailbox.status_expired"),
                    panelX + panelW - 70, panelY + HDR_H + 4, 0xFFCC4444);
        }
    }

    private void renderSmallItem(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().scale(0.75f, 0.75f, 1.0f);
        float inv = 1.0f / 0.75f;
        int sx = Math.round(x * inv);
        int sy = Math.round(y * inv);
        graphics.renderItem(stack, sx, sy);
        graphics.renderItemDecorations(font, stack, sx, sy);
        graphics.pose().popPose();
    }

    // =========================================================================
    // 交互
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (selectedMail == null && button == 0) {
            // 点击邮件行打开详情
            List<Mail> mails = getVisibleMails();
            int start = page * rowsPerPage;
            int end = Math.min(start + rowsPerPage, mails.size());
            for (int i = start; i < end; i++) {
                int rowY = listAreaY + (i - start) * ROW_STRIDE;
                if (mouseX >= panelX + 4 && mouseX <= panelX + panelW - 4
                        && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                    selectedMail = mails.get(i);
                    if (!selectedMail.read) {
                        NetworkHandler.sendToServer(new MailMarkReadC2SPayload(selectedMail.id));
                        selectedMail.read = true; // 乐观更新
                    }
                    init();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && selectedMail != null) { // ESC
            selectedMail = null;
            init();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private List<Mail> getVisibleMails() {
        List<Mail> visible = new ArrayList<>();
        for (Mail m : mailbox.getMails()) {
            if (!m.isExpired()) {
                visible.add(m);
            }
        }
        // 未读在前，按时间倒序
        visible.sort((a, b) -> {
            if (a.read != b.read) return a.read ? 1 : -1;
            return Long.compare(b.sentAt, a.sentAt);
        });
        return visible;
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        for (String paragraph : text.split("\n")) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            int currentWidth = 0;
            for (char c : paragraph.toCharArray()) {
                int charWidth = font.width(String.valueOf(c));
                if (currentWidth + charWidth > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                    currentWidth = 0;
                }
                current.append(c);
                currentWidth += charWidth;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }
}
