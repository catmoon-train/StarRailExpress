// GeneralStatsPanel.java (重写后)
package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.SRE;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Window;

public class GeneralStatsPanel extends AbstractWidget {
    private final SREPlayerStatsComponent stats;
    private final int screenWidth;
    private int scrollY = 0;
    private int maxScroll = 0;
    private boolean isDraggingScrollbar = false;
    private double dragStartY = 0;
    private int dragStartOffset = 0;
    private Font font;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int CONTENT_PAD = 10;
    private static final int CARD_SPACING = 8;
    private static final int CARD_COLOR = 0xFF1A1F2E;

    public GeneralStatsPanel(int x, int y, int width, int height, SREPlayerStatsComponent stats, int screenWidth, int screenHeight) {
        super(x, y, width, height, Component.empty());
        this.stats = stats;
        this.screenWidth = screenWidth;
    }

    public void init() {
        updateScrollMax();
    }

    private void updateScrollMax() {
        int contentHeight = estimateContentHeight();
        maxScroll = Math.max(0, contentHeight - height);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);
    }

    private int estimateContentHeight() {
        int y = 0;
        y += 60; // 玩家信息区
        y += 40; // 通用统计标题
        y += 120; // 通用统计两列
        y += CARD_SPACING;
        y += 4 * 70; // 四个阵营卡片 (每个约70px)
        return y;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        font = Minecraft.getInstance().font;
        // 面板背景
        drawPanelBg(g, getX(), getY(), width, height);

        int areaX = getX() + CONTENT_PAD;
        int areaY = getY() + CONTENT_PAD;
        int areaW = width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 4;
        int areaH = height - CONTENT_PAD * 2;

        enableScissor(areaX, areaY, areaX + areaW, areaY + areaH);

        int currentY = areaY - scrollY;
        int leftColX = areaX;
        int rightColX = areaX + areaW / 2 + 10;

        // ---- 玩家信息卡片 ----
        currentY = drawPlayerCard(g, leftColX, currentY, areaW) + CARD_SPACING;

        // ---- 通用统计标题 ----
        drawSectionHeader(g, leftColX, currentY, areaW, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats"));
        currentY += 25;

        // ---- 通用统计两列 ----
        int statY = currentY;
        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.total_play_time", formatPlayTime(stats.getTotalPlayTime()));
        drawStatPair(g, leftColX + areaW / 2 + 10, statY, "screen." + SRE.MOD_ID + ".player_stats.total_games_played", String.valueOf(stats.getTotalGamesPlayed()));
        statY += 20;
        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.total_kills", String.valueOf(stats.getTotalKills()));
        drawStatPair(g, leftColX + areaW / 2 + 10, statY, "screen." + SRE.MOD_ID + ".player_stats.total_team_kills", String.valueOf(stats.getTotalTeamKills()));
        statY += 20;
        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.total_deaths", String.valueOf(stats.getTotalDeaths()));
        drawStatPair(g, leftColX + areaW / 2 + 10, statY, "screen." + SRE.MOD_ID + ".player_stats.total_wins", String.valueOf(stats.getTotalWins()));
        statY += 20;
        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.total_losses", String.valueOf(stats.getTotalLosses()));
        drawStatPair(g, leftColX + areaW / 2 + 10, statY, "screen." + SRE.MOD_ID + ".player_stats.win_rate", String.format("%.2f%%", getWinRate(stats.getTotalWins(), stats.getTotalGamesPlayed())));
        statY += 20;
//        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.kd_ratio", String.format("%.2f", getKdRatio(stats.getTotalKills(), stats.getTotalDeaths())));
        drawStatPair(g, leftColX, statY, "screen." + SRE.MOD_ID + ".player_stats.total_lovers_wins", String.valueOf(stats.getTotalLoversWins()));

        currentY = statY + 30;

        // ---- 阵营统计卡片 ----
        drawFactionCard(g, leftColX, currentY, areaW,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.civilian_stats").withStyle(s -> s.withColor(0xFF44BB66)),
                stats.getTotalCivilianGames(), stats.getTotalCivilianWins(),
                stats.getTotalCivilianKills(), stats.getTotalCivilianDeaths());
        currentY += 70 + CARD_SPACING;

        drawFactionCard(g, leftColX, currentY, areaW,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.killer_stats").withStyle(s -> s.withColor(0xFFCC2233)),
                stats.getTotalKillerGames(), stats.getTotalKillerWins(),
                stats.getTotalKillerKills(), stats.getTotalKillerDeaths());
        currentY += 70 + CARD_SPACING;

        drawFactionCard(g, leftColX, currentY, areaW,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.neutral_stats").withStyle(s -> s.withColor(0xFFCCAA22)),
                stats.getTotalNeutralGames(), stats.getTotalNeutralWins(),
                stats.getTotalNeutralKills(), stats.getTotalNeutralDeaths());
        currentY += 70 + CARD_SPACING;

        drawFactionCard(g, leftColX, currentY, areaW,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.sheriff_stats").withStyle(s -> s.withColor(0xFF22BBCC)),
                stats.getTotalSheriffGames(), stats.getTotalSheriffWins(),
                stats.getTotalSheriffKills(), stats.getTotalSheriffDeaths());

        disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int scrollbarX = getX() + width - CONTENT_PAD - SCROLLBAR_WIDTH;
            int contentH = estimateContentHeight();
            renderVScrollbar(g, scrollbarX, areaY, areaH, scrollY, maxScroll, contentH, mx, my, isDraggingScrollbar);
        }
    }

    private int drawPlayerCard(GuiGraphics g, int x, int y, int width) {
        int cardHeight = 50;
        drawCardBg(g, x, y, width, cardHeight, 0xFF2A2F3F);

        PlayerInfo info = Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID());
        if (info != null && info.getSkin().texture() != null) {
            ResourceLocation skin = info.getSkin().texture();
            RenderSystem.enableBlend();
            g.pose().pushPose();
            g.pose().translate(x + 5,  y + 5, 0);
            g.pose().scale(4f, 4f, 0);
            float offColour =  1f;
            g.innerBlit(skin, 0, 8, 0, 8, 0, 8 / 64f, 16 / 64f, 8 / 64f, 16 / 64f, 1f, offColour,
                    offColour, 1f);
            g.pose().translate(-0.5, -0.5, 0);
            g.pose().scale(1.125f, 1.125f, 1f);
            g.innerBlit(skin, 0, 8, 0, 8, 0, 40 / 64f, 48 / 64f, 8 / 64f, 16 / 64f, 1f, offColour,
                    offColour, 1f);
            g.pose().popPose();
        } else {
            g.fill(x + 5, y + 5, x + 45, y + 45, 0xFF333333);
        }

        String name = Minecraft.getInstance().player.getDisplayName().getString();
        g.drawString(font, Component.literal(name).withStyle(s -> s.withBold(true).withColor(0xFFFFAA00)),
                x + 55, y + 10, 0xFFFFAA00);
        String currentNameTag = NameTagInventoryComponent.KEY.get(Minecraft.getInstance().player).CurrentNameTag;
        if (currentNameTag != null && !currentNameTag.isEmpty()) {
            g.drawString(font, Component.translatable(currentNameTag).withStyle(s -> s.withColor(0xFF55FF55)),
                    x + 55, y + 22, 0xFF55FF55);
        }
//        g.drawString(font, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.player_id", targetPlayerUuid.toString().substring(0, 8)),
//                x + 55, y + 25, 0xFFAAAAAA);

        return y + cardHeight;
    }

    private void drawSectionHeader(GuiGraphics g, int x, int y, int width, Component title) {
        g.drawString(font, title.copy().withStyle(s -> s.withBold(true)), x, y, 0xFFFFFFFF);
        g.fill(x, y + font.lineHeight + 2, x + width, y + font.lineHeight + 3, 0x33FFFFFF);
    }

    private void drawStatPair(GuiGraphics g, int x, int y, String key, String value) {
        Component label = Component.translatable(key).withStyle(s -> s.withColor(0xFFAAAAAA));
        Component val = Component.literal(value).withStyle(s -> s.withColor(0xFFFFDD88));
        g.drawString(font, label, x, y, 0xFFAAAAAA);
        g.drawString(font, val, x + 100, y, 0xFFFFDD88);
    }

    private void drawFactionCard(GuiGraphics g, int x, int y, int width, Component title, int games, int wins, int kills, int deaths) {
        drawCardBg(g, x, y, width, 65, 0xFF252B38);
        g.drawString(font, title, x + 8, y + 6, title.getStyle().getColor() != null ? title.getStyle().getColor().getValue() : 0xFFFFFFFF);

        int left = x + 8;
        int right = x + width / 2 + 5;

        g.drawString(font, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.games", games), left, y + 22, 0xFFCCCCCC);
        g.drawString(font, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.wins", wins), left, y + 36, 0xFFCCCCCC);
        g.drawString(font, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.win_rate", String.format("%.1f%%", getWinRate(wins, games))), right, y + 22, 0xFFCCCCCC);
//        g.drawString(font, Component.translatable("screen." + SRE.MOD_ID + ".player_stats.kd", String.format("%.2f", getKdRatio(kills, deaths))), right, y + 36, 0xFFCCCCCC);
    }

    private void drawCardBg(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
        g.renderOutline(x, y, w, h, 0xFF3A4050);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    private void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xD80C1020, 0xD8101828);
        g.renderOutline(x, y, w, h, 0xFF1E3060);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
    }

    private void renderVScrollbar(GuiGraphics g, int x, int y, int h, int offset, int max, int totalH, int mx, int my, boolean dragging) {
        g.fill(x, y, x + SCROLLBAR_WIDTH, y + h, 0xFF111828);
        g.fill(x + 1, y + 1, x + SCROLLBAR_WIDTH - 1, y + h - 1, 0x55334466);
        if (max <= 0) return;

        float ratio = Math.min(1f, (float) h / totalH);
        int thumbH = Math.max(20, (int) (h * ratio));
        int thumbY = y + (int) ((h - thumbH) * ((float) offset / max));
        boolean hl = dragging || (mx >= x && mx <= x + SCROLLBAR_WIDTH && my >= thumbY && my <= thumbY + thumbH);
        g.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbH, hl ? 0xFF8899CC : 0xFF556699);
        g.fill(x + 1, thumbY + 1, x + SCROLLBAR_WIDTH - 1, thumbY + thumbH - 1, hl ? 0xFFAABBEE : 0xFF7788BB);
        g.fill(x + 1, thumbY + 1, x + SCROLLBAR_WIDTH - 1, thumbY + 3, 0x44FFFFFF);
    }

    private void enableScissor(int x0, int y0, int x1, int y1) {
        Window w = Minecraft.getInstance().getWindow();
        int scale = (int) w.getGuiScale();
        int sy0 = (int) (w.getScreenHeight() - y1 * scale);
        RenderSystem.enableScissor(x0 * scale, sy0, (x1 - x0) * scale, (y1 - y0) * scale);
    }

    private void disableScissor() { RenderSystem.disableScissor(); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && isMouseOver(mx, my)) {
            int scrollbarX = getX() + width - CONTENT_PAD - SCROLLBAR_WIDTH;
            int areaY = getY() + CONTENT_PAD;
            int areaH = height - CONTENT_PAD * 2;
            if (mx >= scrollbarX && mx <= scrollbarX + SCROLLBAR_WIDTH && my >= areaY && my <= areaY + areaH && maxScroll > 0) {
                isDraggingScrollbar = true;
                dragStartY = my;
                dragStartOffset = scrollY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        isDraggingScrollbar = false;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (isDraggingScrollbar && maxScroll > 0) {
            int areaH = height - CONTENT_PAD * 2;
            int totalH = estimateContentHeight();
            int thumbH = Math.max(20, (int) (areaH * Math.min(1f, (float) areaH / totalH)));
            double trackH = areaH - thumbH;
            if (trackH > 0) {
                scrollY = (int) (dragStartOffset + (my - dragStartY) / trackH * maxScroll);
                scrollY = Mth.clamp(scrollY, 0, maxScroll);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (isMouseOver(mx, my)) {
            scrollY = Mth.clamp(scrollY - (int) (vert * 20), 0, maxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return mx >= getX() && mx <= getX() + width && my >= getY() && my <= getY() + height;
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput out) {}

    public void setVisible(boolean visible) { this.visible = visible; }

    private String formatPlayTime(long ticks) {
        long sec = ticks / 20;
        long min = sec / 60;
        long hour = min / 60;
        long day = hour / 24;
        if (day > 0) return day + "d " + (hour % 24) + "h " + (min % 60) + "m";
        if (hour > 0) return hour + "h " + (min % 60) + "m";
        if (min > 0) return min + "m " + (sec % 60) + "s";
        return sec + "s";
    }

    private double getKdRatio(int kills, int deaths) { return deaths == 0 ? kills : (double) kills / deaths; }
    private double getWinRate(int wins, int games) { return games == 0 ? 0 : (double) wins / games * 100; }
}