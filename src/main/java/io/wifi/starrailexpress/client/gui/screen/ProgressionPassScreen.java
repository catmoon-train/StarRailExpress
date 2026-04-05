package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent.FactionCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class ProgressionPassScreen extends Screen {

    // private static final long OPEN_ANIM_MS = 180L;
    // private static final long CLOSE_ANIM_MS = 150L;

    // ------- layout constants -------
    /** 每行任务高度 */
    private static final int ROW_H = 36;
    /** 行间距 */
    private static final int ROW_STRIDE = ROW_H + 4;
    /** 顶部标题区高度: 标题 + 等级行 + 经验条 + 内边距 */
    private static final int HDR_H = 72;
    /** 统计卡片区高度: 卡片 36 + 间距 8 */
    private static final int SUM_H = 44;
    /** Tab 按钮行高度: 按钮 20 + 间距 4 */
    private static final int TAB_H = 24;
    /** 任务区顶部偏移 = HDR_H + SUM_H + TAB_H */
    private static final int QUEST_TOP = HDR_H + SUM_H + TAB_H; // 140
    /** 分页行高度 */
    private static final int PG_H = 22;
    /** 底部区域高度: 信息文字 + 阵营按钮 */
    private static final int BT_H = 70;

    // ------- 组件状态 -------
    private final SREPlayerProgressionComponent progression;
    private final List<Button> cardButtons = new ArrayList<>();

    private int activeTab = 0; // 0 = 每日, 1 = 周常, 2 = 永久
    private int dailyPage = 0;
    private int weeklyPage = 0;
    private int permanentPage = 0;

    // ------- 布局缓存 (computeLayout → init，render 直接读取) -------
    private int panelX, panelY, panelW, panelH;
    private int questAreaY, questAreaH, rowsPerPage;

    // private long openAnimStartMs;
    private boolean closing;
    // private long closeAnimStartMs;

    private LocalPlayer player;

    public ProgressionPassScreen() {
        super(Component.translatable("sre.pass.name"));
        this.player = Minecraft.getInstance().player;
        this.progression = SREPlayerProgressionComponent.KEY.get(player);
        SREPlayerSkinsComponent.KEY.get(player);
    }

    // =========================================================================
    // 布局计算
    // =========================================================================

    private void computeLayout() {
        panelW = Math.max(240, this.width - 8);
        panelH = Math.max(280, this.height - 8);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        questAreaY = panelY + QUEST_TOP;
        questAreaH = Math.max(ROW_STRIDE, panelH - QUEST_TOP - PG_H - BT_H);
        rowsPerPage = Math.max(1, questAreaH / ROW_STRIDE);
    }

    // =========================================================================
    // 界面初始化
    // =========================================================================

    @Override
    protected void init() {
        clearWidgets();
        cardButtons.clear();
        computeLayout();

        // openAnimStartMs = System.currentTimeMillis();
        closing = false;
        // closeAnimStartMs = 0L;

        // 限制页码在合法范围内
        int dSz = progression.getActiveDailyQuests().size();
        int wSz = progression.getActiveWeeklyQuests().size();
        int pSz = progression.getActivePermanentQuests().size();
        dailyPage = Math.min(dailyPage, Math.max(0, (dSz - 1) / rowsPerPage));
        weeklyPage = Math.min(weeklyPage, Math.max(0, (wSz - 1) / rowsPerPage));
        permanentPage = Math.min(permanentPage, Math.max(0, (pSz - 1) / rowsPerPage));

        // ---- Tab 按钮 ----
        int tabY = panelY + HDR_H + SUM_H;
        int tabW = Math.min(120, (panelW - 64) / 3);
        addRenderableWidget(
                ModernButton.builder(Component.translatable("sre.pass.day"), btn -> {
                    activeTab = 0;
                    init();
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(panelX + 24, tabY, tabW, 20)
                        .accentColor(activeTab == 0 ? 0xFF3AA6FF : 0xFF2B3A55)
                        .build());
        addRenderableWidget(
                ModernButton.builder(Component.translatable("sre.pass.weekly"), btn -> {
                    activeTab = 1;
                    init();
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(panelX + 24 + tabW + 8, tabY, tabW, 20)
                        .accentColor(activeTab == 1 ? 0xFFFFD060 : 0xFF2B3A55)
                        .build());
        addRenderableWidget(
                ModernButton.builder(Component.translatable("sre.pass.permanent"), btn -> {
                    activeTab = 2;
                    init();
                }).accentBar(ModernButton.AccentSide.TOP)
                        .bounds(panelX + 24 + (tabW + 8) * 2, tabY, tabW, 20)
                        .accentColor(activeTab == 2 ? 0xFFA98BFF : 0xFF2B3A55)
                        .build());

        // ---- 分页按钮 ----
        int pgY = questAreaY + questAreaH + 4;
        addRenderableWidget(
                ModernButton.builder(Component.literal("◀"), btn -> {
                    if (activeTab == 0)
                        dailyPage = Math.max(0, dailyPage - 1);
                    else if (activeTab == 1)
                        weeklyPage = Math.max(0, weeklyPage - 1);
                    else
                        permanentPage = Math.max(0, permanentPage - 1);
                }).accentBar(ModernButton.AccentSide.LEFT).bounds(panelX + panelW / 2 - 60, pgY, 48, 20)
                        .accentColor(0xFF2B3A55).build());
        addRenderableWidget(
                ModernButton.builder(Component.literal("▶"), btn -> {
                    int total = activeTab == 0 ? progression.getActiveDailyQuests().size()
                            : activeTab == 1 ? progression.getActiveWeeklyQuests().size()
                                    : progression.getActivePermanentQuests().size();
                    int pages = Math.max(1, (total + rowsPerPage - 1) / rowsPerPage);
                    if (activeTab == 0)
                        dailyPage = Math.min(pages - 1, dailyPage + 1);
                    else if (activeTab == 1)
                        weeklyPage = Math.min(pages - 1, weeklyPage + 1);
                    else
                        permanentPage = Math.min(pages - 1, permanentPage + 1);
                }).accentBar(ModernButton.AccentSide.RIGHT).bounds(panelX + panelW / 2 + 12, pgY, 48, 20)
                        .accentColor(0xFF2B3A55).build());

        // ---- 阵营卡按钮 ----
        int bottomY = panelY + panelH - 38;
        int cardBtnW = Math.max(60, Math.min(120, (panelW - 48 - 144 - 24) / 3));
        cardButtons.add(addRenderableWidget(
                createCardButton(panelX + 24, bottomY, cardBtnW, FactionCardType.KILLER, 0xFFB84141)));
        cardButtons.add(addRenderableWidget(
                createCardButton(panelX + 24 + (cardBtnW + 8), bottomY, cardBtnW, FactionCardType.CIVILIAN,
                        0xFF4EA5D9)));
        cardButtons.add(addRenderableWidget(
                createCardButton(panelX + 24 + (cardBtnW + 8) * 2, bottomY, cardBtnW, FactionCardType.NEUTRAL,
                        0xFFD9A44E)));

        // ---- 关闭按钮 ----
        addRenderableWidget(
                ModernButton.builder(Component.translatable("sre.pass.close"), button -> this.onClose())
                        .bounds(panelX + panelW - 144, bottomY, 128, 22)
                        .accentColor(0xFFE8E8F2)
                        .build());
    }

    private Button createCardButton(int x, int y, int width, FactionCardType type, int accentColor) {
        int count = progression.getFactionCards().getOrDefault(type, 0);
        Component label = Component.literal(
                Component.translatable("sre.pass.faction." + type.questKey).getString() + " x" + count);
        var btn = ModernButton.builder(label, b -> {
            if (count > 0) {
                sendCommand("sre:pass activate " + type.questKey);
                onClose();
            }
        })
                .bounds(x, y, width, 22).accentColor(accentColor).build();
        btn.active = count > 0;
        return btn;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            // closeAnimStartMs = System.currentTimeMillis();
            return;
        }
        super.onClose();
    }

    // =========================================================================
    // 渲染
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float animVisibility = getAnimationVisibility();
        if (closing && animVisibility <= 0.01f) {
            super.onClose();
            return;
        }
        int animAlpha = Math.max(0, Math.min(255, (int) (animVisibility * 255.0f)));
        super.render(g, mouseX, mouseY, partialTick);

        // ---- 标题栏 ----
        g.drawString(font, Component.translatable("sre.pass.name"), panelX + 24, panelY + 20,
                applyAlpha(0xFFF4F7FF, animAlpha), false);
        g.drawString(font, "Lv." + progression.getLevel()
                + "  "
                + Component.translatable("sre.pass.exp_progress", progression.getExperience(),
                        progression.getExperienceForNextLevel()).getString(),
                panelX + 24, panelY + 34, applyAlpha(0xFF92B6E5, animAlpha), false);

        // ---- 统计卡片 ----
        renderSummaryCards(g, panelX + 24, panelY + HDR_H);

        // ---- 任务行 ----
        List<SREPlayerProgressionComponent.PassQuest> quests = activeTab == 0 ? progression.getActiveDailyQuests()
                : activeTab == 1 ? progression.getActiveWeeklyQuests()
                        : progression.getActivePermanentQuests();
        int curPage = activeTab == 0 ? dailyPage : activeTab == 1 ? weeklyPage : permanentPage;
        int start = curPage * rowsPerPage;
        int end = Math.min(start + rowsPerPage, quests.size());
        int rowY = questAreaY + 4;
        for (int i = start; i < end; i++) {
            renderQuestRow(g, panelX + 24, rowY, panelW - 48, quests.get(i), animAlpha);
            rowY += ROW_STRIDE;
        }
        if (quests.isEmpty()) {
            g.drawString(font, Component.translatable("sre.no_quest"), panelX + 24 + 16, questAreaY + 16,
                    applyAlpha(0xFF6A7D99, animAlpha), false);
        }

        // ---- 分页页码标签 ----
        int totalPages = Math.max(1, (quests.size() + rowsPerPage - 1) / rowsPerPage);
        String pgLabel = (curPage + 1) + " / " + totalPages;
        int pgY = questAreaY + questAreaH + 4;
        g.drawString(font, pgLabel, panelX + panelW / 2 - font.width(pgLabel) / 2, pgY + 6,
                applyAlpha(0xFF9BBAD4, animAlpha), false);

        // ---- 底部信息 ----
        int infoY = panelY + panelH - 62;
        // 不保存这一项所以删了
        // String activeCard = true
        // ? Component.translatable("sre.pass.not_active").getString()
        // : Component.translatable("sre.pass.faction." +
        // progression.getActiveFactionCard().questKey,
        // Component.translatable(progression.getActiveFactionCard().displayName)).getString();
        // g.drawString(font, Component.translatable("sre.pass.active_card",
        // activeCard).getString(), panelX + 24, infoY,
        // applyAlpha(0xFFF7D791, animAlpha), false);

        long dailyRem = Math.max(0L, progression.getLastQuestRefreshTime()
                + 24L * 60L * 60L * 1000L - System.currentTimeMillis());
        long weeklyRem = Math.max(0L, progression.getLastWeeklyRefreshTime()
                + 7L * 24L * 60L * 60L * 1000L - System.currentTimeMillis());
        g.drawString(font, Component.translatable("sre.pass.daily_refresh", formatDuration(dailyRem)).getString(),
                panelX + 24, infoY + 14, applyAlpha(0xFF86A3C5, animAlpha), false);
        g.drawString(font, Component.translatable("sre.pass.weekly_refresh", formatDuration(weeklyRem)).getString(),
                panelX + panelW / 2 + 8, infoY + 14, applyAlpha(0xFFB09FFF, animAlpha), false);

        // 对组件层加一个淡入/淡出遮罩，打开时从黑渐显，关闭时渐隐到黑。
        int overlayAlpha = 255 - animAlpha;
        if (overlayAlpha > 0) {
            g.fill(0, 0, width, height, (overlayAlpha << 24));
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float animVisibility = getAnimationVisibility();

        int animAlpha = Math.max(0, Math.min(255, (int) (animVisibility * 255.0f)));

        // ---- 面板背景 ----
        g.fillGradient(0, 0, width, height, applyAlpha(0xE0080C14, animAlpha), applyAlpha(0xF0121B2E, animAlpha));
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, applyAlpha(0xD20C1018, animAlpha));
        g.fill(panelX + 14, panelY + 14, panelX + panelW - 14, panelY + 16, applyAlpha(0xFF3AA6FF, animAlpha));
        g.fill(panelX + 14, panelY + panelH - 14, panelX + panelW - 14, panelY + panelH - 12,
                applyAlpha(0x6648D3FF, animAlpha));
        renderProgressBar(g, panelX + 24, panelY + 52, panelW - 48, 10, animAlpha);
        // ---- 任务列表背景 ----
        g.fill(panelX + 24, questAreaY, panelX + panelW - 24, questAreaY + questAreaH,
                applyAlpha(activeTab == 0 ? 0x8A111827 : 0x8A11211F, animAlpha));
    }

    // =========================================================================
    // 子渲染器
    // =========================================================================

    private void renderProgressBar(GuiGraphics g, int x, int y, int width, int height, int animAlpha) {
        g.fill(x, y, x + width, y + height, applyAlpha(0x55334955, animAlpha));
        int filled = (int) (width * (progression.getExperience()
                / (float) Math.max(1, progression.getExperienceForNextLevel())));
        g.fill(x, y, x + filled, y + height, applyAlpha(0xFF47C1FF, animAlpha));
        g.fill(x + filled, y, x + Math.min(width, filled + 20), y + height, applyAlpha(0xAA9BE2FF, animAlpha));
    }

    private void renderSummaryCards(GuiGraphics g, int startX, int y) {
        int cardW = (panelW - 72) / 3;
        renderSummaryCard(g, startX, y, cardW, Component.translatable("sre.pass.total_exp").getString(),
                String.valueOf(progression.getTotalExperience()), 0xFF61D0FF);
        renderSummaryCard(g, startX + cardW + 12, y, cardW, Component.translatable("sre.pass.coin_reward").getString(),
                String.valueOf(progression.getClaimedCoinRewards()), 0xFFFFD166);
        renderSummaryCard(g, startX + cardW * 2 + 24, y, cardW,
                Component.translatable("sre.pass.loot_count").getString(),
                String.valueOf(progression.getClaimedLootRewards()), 0xFFCDB4FF);
    }

    private void renderSummaryCard(GuiGraphics g, int x, int y, int w, String label, String value, int accent) {
        g.fill(x, y, x + w, y + 36, 0xA5161D2C);
        g.fill(x, y, x + 4, y + 36, accent);
        g.drawString(font, label, x + 10, y + 6, 0xFF90A5C1, false);
        g.drawString(font, value, x + 10, y + 18, 0xFFF5FAFF, false);
    }

    private void renderQuestRow(GuiGraphics g, int questX, int rowY, int questW,
            SREPlayerProgressionComponent.PassQuest quest, int animAlpha) {
        int innerX = questX + 12;
        int innerW = questW - 24;
        g.fill(innerX, rowY, innerX + innerW, rowY + ROW_H, applyAlpha(0x66192135, animAlpha));

        // 右列：进度比 + 进度条（宽度自适应）
        int barW = Math.min(160, Math.max(50, innerW / 3));
        int barX = innerX + innerW - barW - 8;
        String frac = quest.progress + "/" + quest.target;
        int fracW = font.width(frac);
        g.drawString(font, frac, barX - fracW - 6, rowY + 3, applyAlpha(0xFFE8EEF8, animAlpha), false);
        g.fill(barX, rowY + 14, barX + barW, rowY + 19, applyAlpha(0x55384A66, animAlpha));
        int barFilled = (int) (barW * (quest.progress / (float) Math.max(1, quest.target)));
        g.fill(barX, rowY + 14, barX + barFilled, rowY + 19,
                applyAlpha(quest.rewarded ? 0xFF3DE4A8 : 0xFF59A9FF, animAlpha));

        // 左列：标题 / 描述 / 奖励（紧凑格式）
        int titleColor = quest.rewarded ? 0xFF7CFFC0 : 0xFFF3F7FF;
        g.drawString(font, quest.title, innerX + 8, rowY + 3, applyAlpha(titleColor, animAlpha), false);
        g.drawString(font, quest.description, innerX + 8, rowY + 13, applyAlpha(0xFF8FA7C4, animAlpha), false);
        String rwd = "+" + quest.rewardExperience + "exp  +" + quest.rewardCoins + "g"
                + (quest.rewardLoot > 0 ? "  +" + quest.rewardLoot + "L" : "")
                + (quest.rewardCard != FactionCardType.NONE
                        ? "  " + Component.translatable("sre.pass.faction." + quest.rewardCard.questKey,
                                Component.translatable(quest.rewardCard.displayName)).getString()
                        : "");
        g.drawString(font, rwd, innerX + 8, rowY + 23, applyAlpha(0xFFF7D27A, animAlpha), false);
    }

    // =========================================================================
    // 输入处理
    // =========================================================================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) {
            if (activeTab == 0)
                dailyPage = Math.max(0, dailyPage - 1);
            else if (activeTab == 1)
                weeklyPage = Math.max(0, weeklyPage - 1);
            else
                permanentPage = Math.max(0, permanentPage - 1);
        } else if (scrollY < 0) {
            int total = activeTab == 0 ? progression.getActiveDailyQuests().size()
                    : activeTab == 1 ? progression.getActiveWeeklyQuests().size()
                            : progression.getActivePermanentQuests().size();
            int pages = Math.max(1, (total + rowsPerPage - 1) / rowsPerPage);
            if (activeTab == 0)
                dailyPage = Math.min(pages - 1, dailyPage + 1);
            else if (activeTab == 1)
                weeklyPage = Math.min(pages - 1, weeklyPage + 1);
            else
                permanentPage = Math.min(pages - 1, permanentPage + 1);
        }
        return true;
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null)
            return;
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    private static String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        return d.toHours() + "h " + d.toMinutesPart() + "m";
    }

    private float getAnimationVisibility() {
        // long now = System.currentTimeMillis();
        if (closing) {
            return 0;
        }
        // float t = (now - openAnimStartMs) / (float) OPEN_ANIM_MS;
        return 1;
    }

    // private static float clamp01(float value) {
    // return Math.max(0.0f, Math.min(1.0f, value));
    // }

    // private static float easeOutCubic(float t) {
    // float inv = 1.0f - t;
    // return 1.0f - inv * inv * inv;
    // }

    private static int applyAlpha(int color, int alpha) {
        int a = (color >>> 24) & 0xFF;
        int scaled = (a * alpha) / 255;
        return (scaled << 24) | (color & 0x00FFFFFF);
    }
}
