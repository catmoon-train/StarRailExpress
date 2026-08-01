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

package net.exmo.sre.record.client;

import io.wifi.starrailexpress.api.replay.ReplayDisplayUtils;
import net.exmo.sre.record.MatchRecord;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局战绩 / 对局回放 GUI —— 参考游戏介绍（Intro）界面的暗金主从布局：
 * 左侧为对局卡片列表（虚拟列表，按滚动位置按需向服务端拉取分页），
 * 右侧为所选对局的详情：玩家按阵营分组（显示本地化职业名与旧职业）+ 事件时间线。
 * 不展示 Store Buy（商店购买）与隐藏事件。
 */
public class MatchRecordsScreen extends Screen {

    private static final String STORE_BUY = "STORE_BUY";
    /** 每页拉取的战绩条数。 */
    public static final int PAGE_SIZE = 16;

    // ── 布局常量（对齐 RoleIntroduceScreen 的观感） ──
    private static final int MAX_USABLE_WIDTH = 760;
    private static final float USABLE_RATIO = 0.9f;
    private static final float LEFT_RATIO = 0.34f;
    private static final int PANEL_PAD = 6;
    private static final int CARD_H = 38;
    private static final int CARD_SPACING = 4;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int BANNER_H = 26;

    // ── 调色板 ──
    private static final int PANEL_BG_TOP = 0xD81A1008;
    private static final int PANEL_BG_BOTTOM = 0xD820140A;
    private static final int PANEL_BORDER = 0xFF8B6914;
    private static final int GOLD = 0xFFD4AF37;
    private static final int GOLD_DIM = 0xFFB8960C;
    private static final int CREAM = 0xFFF5E8C8;
    private static final int TAN = 0xFF9E8B6E;

    // 阵营展示顺序（好人→治安官→中立→中立偏杀→杀手→未知）
    private static final int[] FACTION_ORDER = { 1, 5, 2, 3, 4, 0 };

    private final Screen parent;
    private String selectedMatchId;
    private int selectedIndex = -1;
    private MatchRecord selectedRecord;
    private final List<FormattedCharSequence> detailLines = new ArrayList<>();

    private int usableWidth, leftW, rightW;
    private int panelX, panelY, panelH, leftX, rightX;

    private int listScrollOffset;
    private int maxListScroll;
    private boolean draggingListScroll;
    private double dragListStartY;
    private int dragListStartOffset;

    private int detailScrollOffset;
    private int maxDetailScroll;
    private boolean draggingDetailScroll;
    private double dragDetailStartY;
    private int dragDetailStartOffset;

    public MatchRecordsScreen() {
        this((Screen) null);
    }

    public MatchRecordsScreen(Screen parent) {
        super(Component.translatable("screen.sre.records.list.title"));
        this.parent = parent;
    }

    public MatchRecordsScreen(MatchRecord initial) {
        this((Screen) null);
        if (initial != null) {
            this.selectedRecord = initial;
            this.selectedMatchId = initial.matchId;
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected void init() {
        super.init();
        usableWidth = Math.min((int) (width * USABLE_RATIO), MAX_USABLE_WIDTH);
        leftW = (int) (usableWidth * LEFT_RATIO);
        rightW = usableWidth - leftW;
        panelX = (width - usableWidth) / 2;
        panelY = 48;
        panelH = height - panelY - 42;
        leftX = panelX;
        rightX = panelX + leftW;
        ensurePage(0);
        onWindowUpdated();
        rebuildDetailLines();
    }

    // ══════════════════════════════════════════════════════════════════
    // 虚拟列表数据访问
    // ══════════════════════════════════════════════════════════════════

    private int total() {
        return ClientMatchRecordCache.getTotal();
    }

    private MatchRecord.Summary at(int index) {
        return ClientMatchRecordCache.getAt(index);
    }

    /** 确保某下标所在分页已被请求（去重，仅首次发包）。 */
    private void ensurePage(int index) {
        if (index < 0) {
            return;
        }
        int page = index / PAGE_SIZE;
        if (ClientMatchRecordCache.markPageRequested(page)) {
            MatchRecordClientNetwork.requestWindow(page * PAGE_SIZE, PAGE_SIZE);
        }
    }

    /** 收到新一页数据时刷新（不打断当前选择，必要时补完首选）。 */
    public void onWindowUpdated() {
        recomputeListScroll();
        if (selectedMatchId == null && total() > 0) {
            selectIndex(0);
        } else if (selectedRecord == null && selectedIndex >= 0) {
            MatchRecord.Summary summary = at(selectedIndex);
            if (summary != null && summary.matchId != null && summary.matchId.equals(selectedMatchId)) {
                loadSelected(summary.matchId);
            }
        }
    }

    /** 收到某场完整回放数据时填充右侧详情。 */
    public void showRecord(MatchRecord record) {
        if (record == null) {
            return;
        }
        if (selectedMatchId == null || selectedMatchId.equals(record.matchId)) {
            selectedMatchId = record.matchId;
            selectedRecord = record;
            detailScrollOffset = 0;
            rebuildDetailLines();
        }
    }

    private void selectIndex(int index) {
        if (index < 0 || index >= total()) {
            return;
        }
        selectedIndex = index;
        ensurePage(index);
        MatchRecord.Summary summary = at(index);
        if (summary != null && summary.matchId != null) {
            selectedMatchId = summary.matchId;
            detailScrollOffset = 0;
            loadSelected(summary.matchId);
        }
        int cardY = index * (CARD_H + CARD_SPACING);
        if (cardY < listScrollOffset) {
            listScrollOffset = cardY;
        } else if (cardY + CARD_H > listScrollOffset + listAreaH()) {
            listScrollOffset = cardY + CARD_H - listAreaH();
        }
        recomputeListScroll();
    }

    private void loadSelected(String matchId) {
        MatchRecord cached = ClientMatchRecordCache.getRecord(matchId);
        if (cached != null) {
            selectedRecord = cached;
        } else {
            selectedRecord = null;
            MatchRecordClientNetwork.requestReplay(matchId);
        }
        rebuildDetailLines();
    }

    private int listAreaH() {
        return panelH - PANEL_PAD * 2;
    }

    private int detailContentH() {
        return panelH - BANNER_H - PANEL_PAD * 2 - 4;
    }

    private void recomputeListScroll() {
        int totalH = total() * (CARD_H + CARD_SPACING);
        maxListScroll = Math.max(0, totalH - listAreaH());
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxListScroll);
    }

    // ══════════════════════════════════════════════════════════════════
    // 详情（按阵营分组 + 本地化职业名 + 旧职业）
    // ══════════════════════════════════════════════════════════════════

    private void rebuildDetailLines() {
        detailLines.clear();
        int textW = rightW - PANEL_PAD * 2 - SCROLL_W - 4;
        if (selectedMatchId == null) {
            maxDetailScroll = 0;
            return;
        }
        if (selectedRecord == null) {
            detailLines.addAll(font.split(Component.translatable("screen.sre.records.loading")
                    .withStyle(ChatFormatting.GRAY), textW));
            maxDetailScroll = 0;
            return;
        }

        String dashes = dashes(textW);

        if (selectedRecord.players != null && !selectedRecord.players.isEmpty()) {
            for (int faction : FACTION_ORDER) {
                appendFactionGroup(faction, dashes, textW);
            }
            detailLines.add(FormattedCharSequence.EMPTY);
        }

        // 时间线
        detailLines.addAll(font.split(Component.translatable("screen.sre.records.detail.timeline")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), textW));
        detailLines.addAll(font.split(Component.literal(dashes).withStyle(ChatFormatting.DARK_GRAY), textW));
        boolean any = false;
        if (selectedRecord.events != null) {
            for (MatchRecord.MatchEvent event : selectedRecord.events) {
                if (event == null || event.hidden || STORE_BUY.equals(event.type)) {
                    continue;
                }
                any = true;
                MutableComponent line = Component.literal(ReplayDisplayUtils.formatTime(event.relativeTimestamp) + " ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(RecordClientUtil.parseComponent(event.textJson));
                detailLines.addAll(font.split(line, textW));
            }
        }
        if (!any) {
            detailLines.addAll(font.split(Component.translatable("screen.sre.records.detail.no_events")
                    .withStyle(ChatFormatting.DARK_GRAY), textW));
        }

        int totalH = detailLines.size() * (font.lineHeight + 2);
        maxDetailScroll = Math.max(0, totalH - detailContentH());
        detailScrollOffset = Mth.clamp(detailScrollOffset, 0, maxDetailScroll);
    }

    private void appendFactionGroup(int faction, String dashes, int textW) {
        List<MatchRecord.MatchPlayer> members = new ArrayList<>();
        for (MatchRecord.MatchPlayer player : selectedRecord.players) {
            if (factionBucket(player.faction) == faction) {
                members.add(player);
            }
        }
        if (members.isEmpty()) {
            return;
        }
        ChatFormatting color = factionColor(faction);
        MutableComponent header = Component.translatable(factionKey(faction)).withStyle(color, ChatFormatting.BOLD);
        header.append(Component.literal(" (" + members.size() + ")").withStyle(ChatFormatting.DARK_GRAY));
        detailLines.addAll(font.split(header, textW));
        detailLines.addAll(font.split(Component.literal(dashes).withStyle(ChatFormatting.DARK_GRAY), textW));
        for (MatchRecord.MatchPlayer player : members) {
            MutableComponent line = Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(player.name == null ? "?" : player.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  "))
                    .append(ReplayDisplayUtils.getRoleDisplayName(player.roleId).withStyle(color));
            if (player.oldRoleId != null && !player.oldRoleId.isBlank()) {
                line.append(Component.literal("  "))
                        .append(Component.translatable("screen.sre.records.detail.old_role",
                                ReplayDisplayUtils.getRoleDisplayName(player.oldRoleId))
                                .withStyle(ChatFormatting.DARK_GRAY));
            }
            detailLines.addAll(font.split(line, textW));
        }
        detailLines.add(FormattedCharSequence.EMPTY);
    }

    private static int factionBucket(int faction) {
        return (faction >= 1 && faction <= 5) ? faction : 0;
    }

    private static String factionKey(int faction) {
        return switch (faction) {
            case 1 -> "display.type.role.innocent";
            case 2 -> "display.type.role.neutral";
            case 3 -> "display.type.role.neutral_for_killer";
            case 4 -> "display.type.role.killer";
            case 5 -> "display.type.role.vigilante";
            default -> "screen.sre.records.faction.unknown";
        };
    }

    private static ChatFormatting factionColor(int faction) {
        return switch (faction) {
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.YELLOW;
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            case 4 -> ChatFormatting.RED;
            case 5 -> ChatFormatting.AQUA;
            default -> ChatFormatting.GRAY;
        };
    }

    private String dashes(int textW) {
        int count = textW / Math.max(1, font.width("─"));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("─");
        }
        return sb.toString();
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderLeftPanel(g, mouseX, mouseY);
        renderRightPanel(g, mouseX, mouseY);

        g.fillGradient(0, 0, width, panelY - 8, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, title, width / 2, 8, CREAM);
        g.drawCenteredString(font, Component.translatable("screen.sre.records.hint").withStyle(ChatFormatting.GRAY),
                width / 2, height - 24, TAN);
    }

    private void renderLeftPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, leftX, panelY, leftW, panelH);
        recomputeListScroll();
        int areaX = leftX + PANEL_PAD;
        int areaY = panelY + PANEL_PAD;
        int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
        int areaH = listAreaH();
        int total = total();

        if (total <= 0) {
            g.drawCenteredString(font, Component.translatable("screen.sre.records.empty").withStyle(ChatFormatting.GRAY),
                    leftX + leftW / 2, areaY + areaH / 2, TAN);
        } else {
            g.enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);
            int firstVis = Math.max(0, listScrollOffset / (CARD_H + CARD_SPACING));
            int lastVis = Math.min(total - 1, (listScrollOffset + areaH) / (CARD_H + CARD_SPACING));
            for (int i = firstVis; i <= lastVis; i++) {
                int cardY = areaY + i * (CARD_H + CARD_SPACING) - listScrollOffset;
                MatchRecord.Summary summary = at(i);
                if (summary == null) {
                    ensurePage(i);
                    renderPlaceholder(g, areaX, cardY, areaW);
                    continue;
                }
                boolean hovered = isInRect(mouseX, mouseY, areaX, cardY, areaW, CARD_H);
                boolean selected = summary.matchId != null && summary.matchId.equals(selectedMatchId);
                renderCard(g, summary, areaX, cardY, areaW, CARD_H, hovered, selected);
            }
            g.disableScissor();
        }

        int sbX = leftX + leftW - PANEL_PAD - SCROLL_W;
        int totalH = Math.max(1, total * (CARD_H + CARD_SPACING));
        renderVScrollbar(g, sbX, areaY, areaH, listScrollOffset, maxListScroll, totalH, mouseX, mouseY,
                draggingListScroll);
    }

    private void renderPlaceholder(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + CARD_H, 0xFF5A4530);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + CARD_H - 1, 0xFF150D06, 0xFF0E0804);
        g.drawCenteredString(font, Component.translatable("screen.sre.records.loading_row")
                .withStyle(ChatFormatting.DARK_GRAY), x + w / 2, y + (CARD_H - font.lineHeight) / 2, TAN);
    }

    private void renderCard(GuiGraphics g, MatchRecord.Summary summary, int x, int y, int w, int h,
            boolean hovered, boolean selected) {
        int outerBorder = selected ? GOLD : (hovered ? 0xFFC9A84C : 0xFF5A4530);
        g.fill(x, y, x + w, y + h, outerBorder);
        int bgL = selected ? 0xFF5A4520 : (hovered ? 0xFF2A1C0E : 0xFF1A1008);
        int bgR = selected ? 0xFF3A2A10 : (hovered ? 0xFF1C1206 : 0xFF120A04);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, bgL, bgR);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, selected ? 0x44FFE8C0 : 0x10FFFFFF);

        int accent = summary.winningTeam != null || summary.winningTitleJson != null ? GOLD_DIM : 0xFF5A4530;
        g.fill(x + 1, y + 1, x + 4, y + h - 1, accent);

        int textX = x + 8;
        int textMaxW = w - (textX - x) - 6;
        g.drawString(font, font.plainSubstrByWidth(RecordClientUtil.formatDate(summary.createdAt), textMaxW),
                textX, y + 5, selected ? GOLD : TAN, false);

        Component winner = summary.winningTitleJson != null
                ? RecordClientUtil.parseComponent(summary.winningTitleJson)
                : (summary.winningTeam != null
                        ? Component.literal(summary.winningTeam)
                        : Component.translatable("screen.sre.records.no_winner"));
        List<FormattedCharSequence> winnerLines = font.split(winner, textMaxW);
        if (!winnerLines.isEmpty()) {
            g.drawString(font, winnerLines.get(0), textX, y + 7 + font.lineHeight,
                    selected ? CREAM : 0xFFE8D8B0, false);
        }

        Component count = Component.translatable("screen.sre.records.player_count", summary.playerCount);
        g.drawString(font, count, x + w - font.width(count) - 6, y + (h - font.lineHeight) / 2, TAN, false);
    }

    private void renderRightPanel(GuiGraphics g, int mouseX, int mouseY) {
        drawPanelBg(g, rightX, panelY, rightW, panelH);

        if (selectedMatchId == null) {
            g.drawCenteredString(font, Component.translatable("screen.sre.records.select_hint")
                    .withStyle(ChatFormatting.GRAY), rightX + rightW / 2, panelY + panelH / 2, TAN);
            return;
        }

        g.fillGradient(rightX + 1, panelY + 1, rightX + rightW / 2, panelY + BANNER_H, 0xCC8B6914, 0x448B6914);
        g.fillGradient(rightX + rightW / 2, panelY + 1, rightX + rightW - 1, panelY + BANNER_H, 0x448B6914, 0x00000000);

        Component bannerTitle;
        if (selectedRecord != null && selectedRecord.winningTitleJson != null) {
            bannerTitle = RecordClientUtil.parseComponent(selectedRecord.winningTitleJson);
        } else if (selectedRecord != null && selectedRecord.winningTeam != null) {
            bannerTitle = Component.literal(selectedRecord.winningTeam);
        } else {
            bannerTitle = Component.translatable("screen.sre.records.replay.title");
        }
        g.drawString(font, bannerTitle, rightX + PANEL_PAD, panelY + (BANNER_H - font.lineHeight) / 2, CREAM, true);
        if (selectedRecord != null) {
            String date = RecordClientUtil.formatDate(selectedRecord.createdAt);
            g.drawString(font, date, rightX + rightW - font.width(date) - PANEL_PAD,
                    panelY + (BANNER_H - font.lineHeight) / 2, 0xFFE8D8B0, true);
        }

        int textX0 = rightX + PANEL_PAD;
        int textY0 = panelY + BANNER_H + PANEL_PAD;
        int contentH = detailContentH();
        g.enableScissor(rightX + 1, textY0, rightX + rightW - SCROLL_W - 2, textY0 + contentH);
        int lineH = font.lineHeight + 2;
        int lineY = textY0 - detailScrollOffset;
        for (FormattedCharSequence line : detailLines) {
            if (lineY + lineH > textY0 && lineY < textY0 + contentH) {
                g.drawString(font, line, textX0, lineY, 0xFFE6ECF5, false);
            }
            lineY += lineH;
        }
        g.disableScissor();

        renderVScrollbar(g, rightX + rightW - PANEL_PAD - SCROLL_W, textY0, contentH,
                detailScrollOffset, maxDetailScroll, Math.max(1, detailLines.size() * lineH),
                mouseX, mouseY, draggingDetailScroll);
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_BG_TOP, PANEL_BG_BOTTOM);
        g.renderOutline(x, y, w, h, PANEL_BORDER);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFE8C0);
    }

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h, int scrollOffset, int maxScroll,
            int totalContentH, int mouseX, int mouseY, boolean dragging) {
        g.fill(x, y, x + SCROLL_W, y + h, 0xFF1A1008);
        g.fill(x + 1, y + 1, x + SCROLL_W - 1, y + h - 1, 0x558B6914);
        if (maxScroll <= 0) {
            return;
        }
        float ratio = Math.min(1f, (float) h / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hl = dragging || isInRect(mouseX, mouseY, x, thumbY, SCROLL_W, thumbH);
        g.fill(x, thumbY, x + SCROLL_W, thumbY + thumbH, hl ? 0xFFC9A84C : PANEL_BORDER);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + thumbH - 1, hl ? GOLD : GOLD_DIM);
        g.fill(x + 1, thumbY + 1, x + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    // ══════════════════════════════════════════════════════════════════
    // 输入
    // ══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int areaX = leftX + PANEL_PAD;
            int areaY = panelY + PANEL_PAD;
            int areaW = leftW - PANEL_PAD * 2 - SCROLL_W - 2;
            int areaH = listAreaH();
            if (isInRect((int) mx, (int) my, areaX, areaY, areaW, areaH)) {
                int index = (int) ((my - areaY + listScrollOffset) / (CARD_H + CARD_SPACING));
                if (index >= 0 && index < total() && at(index) != null) {
                    selectIndex(index);
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                }
                return true;
            }
            int lsbX = leftX + leftW - PANEL_PAD - SCROLL_W;
            if (isInRect((int) mx, (int) my, lsbX, areaY, SCROLL_W, areaH) && maxListScroll > 0) {
                draggingListScroll = true;
                dragListStartY = my;
                dragListStartOffset = listScrollOffset;
                return true;
            }
            int rsbX = rightX + rightW - PANEL_PAD - SCROLL_W;
            int textY0 = panelY + BANNER_H + PANEL_PAD;
            if (isInRect((int) mx, (int) my, rsbX, textY0, SCROLL_W, detailContentH()) && maxDetailScroll > 0) {
                draggingDetailScroll = true;
                dragDetailStartY = my;
                dragDetailStartOffset = detailScrollOffset;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingListScroll && maxListScroll > 0) {
            int areaH = listAreaH();
            int totalH = Math.max(1, total() * (CARD_H + CARD_SPACING));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (areaH * Math.min(1f, (float) areaH / totalH)));
            double trackH = areaH - thumbH;
            if (trackH > 0) {
                listScrollOffset = Mth.clamp(
                        (int) (dragListStartOffset + (my - dragListStartY) / trackH * maxListScroll),
                        0, maxListScroll);
            }
            return true;
        }
        if (draggingDetailScroll && maxDetailScroll > 0) {
            int contentH = detailContentH();
            int totalH = Math.max(1, detailLines.size() * (font.lineHeight + 2));
            int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (contentH * Math.min(1f, (float) contentH / totalH)));
            double trackH = contentH - thumbH;
            if (trackH > 0) {
                detailScrollOffset = Mth.clamp(
                        (int) (dragDetailStartOffset + (my - dragDetailStartY) / trackH * maxDetailScroll),
                        0, maxDetailScroll);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingListScroll = false;
        draggingDetailScroll = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= leftX && mx < leftX + leftW && my >= panelY && my < panelY + panelH) {
            listScrollOffset = Mth.clamp((int) (listScrollOffset - scrollY * (CARD_H + CARD_SPACING)),
                    0, maxListScroll);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightW && my >= panelY && my < panelY + panelH) {
            detailScrollOffset = Mth.clamp((int) (detailScrollOffset - scrollY * (font.lineHeight + 2) * 3),
                    0, maxDetailScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 265 || keyCode == 264) && total() > 0) {
            int base = selectedIndex < 0 ? 0 : selectedIndex;
            selectIndex(Mth.clamp(base + (keyCode == 265 ? -1 : 1), 0, total() - 1));
            return true;
        }
        if (keyCode == 82) { // R 刷新
            ClientMatchRecordCache.resetWindows();
            selectedIndex = -1;
            ensurePage(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean isInRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }
}
