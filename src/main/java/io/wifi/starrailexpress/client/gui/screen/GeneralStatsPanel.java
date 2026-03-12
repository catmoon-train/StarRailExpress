package io.wifi.starrailexpress.client.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;

import io.wifi.starrailexpress.cca.PlayerStatsComponent;
import io.wifi.starrailexpress.util.MathHelper;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class GeneralStatsPanel extends AbstractWidget {
    private final PlayerStatsComponent stats;
    private final int screenWidth;
    private int scrollY = 0; // 当前滚动偏移（像素）
    private int maxScroll = 0; // 最大滚动距离（内容高度 - 容器高度）
    private boolean isDraggingScrollbar = false; // 是否正在拖动滚动条
    private int scrollbarClickedY = 0; // 点击滑块时鼠标的 Y 坐标（相对于滑块顶部）
    private final List<Renderable> renderables = new ArrayList<>();
    private final List<GuiEventListener> children = new ArrayList<>();
    // 滚动条尺寸
    private static final int totalContentHeight = 400;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_COLOR = 0xFF808080; // 轨道颜色
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFC0C0C0; // 滑块颜色

    public GeneralStatsPanel(int x, int y, int width, int height, PlayerStatsComponent stats, int screenWidth,
            int screenHeight) {
        super(x, y, width, height, Component.empty());
        this.stats = stats;
        this.screenWidth = screenWidth;
        setupComponents();
    }
    public void init(){
        updateScrollMax();
    }
    public void updateScrollMax() {
        maxScroll = Math.max(0, totalContentHeight - this.height);
    }
    // @Override
    // public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
    // double scrollY) {
    // this.scrollY += (int) scrollY;
    // if (this.scrollY > this.maxScroll) {
    // this.scrollY = this.maxScroll;
    // }
    // if (this.scrollY < 0) {
    // this.scrollY = 0;
    // }
    // return true;
    // }

    /**
     * 重新计算最大滚动距离
     */
    // private void recalcMaxScroll(int totalContentHeight) {
    // // for (GuiEventListener child : children) {
    // // // 假设所有子组件垂直排列，y 坐标是相对于容器顶部的绝对位置
    // // // 你需要根据实际布局计算总高度，这里简化：每个子组件的高度累加
    // // totalContentHeight += child.getRectangle().height();
    // // }
    // maxScroll = Math.max(0, totalContentHeight - this.height);
    // // 如果当前滚动超出新范围，调整
    // scrollY = Mth.clamp(scrollY, 0, maxScroll);
    // }

    private void addRenderable(Renderable renderable) {
        renderables.add(renderable);
        if (renderable instanceof GuiEventListener) {
            children.add((GuiEventListener) renderable);
        }
    }

    private void addWidget(GuiEventListener widget) {
        children.add(widget);
        if (widget instanceof Renderable) {
            renderables.add((Renderable) widget);
        }
    }

    private void setupComponents() {
        // 不需要创建子组件，所有渲染在 renderWidget 中完成
        // 保留内部组件作为渲染对象
        // ResourceLocation skinTexture = getPlayerSkinTexture();
        // if (skinTexture != null) {
        // // PlayerHeadComponent headComponent = new PlayerHeadComponent(
        // // getX() + 10,
        // // getY(),
        // // 32,
        // // skinTexture);
        // // addRenderable(headComponent);
        // }
        // 底部贴图
        // BottomTextureComponent bottomTexture = new BottomTextureComponent(
        // getX() + 10,
        // 520,
        // getWidth() - 2 * 10,
        // 64);
        // addRenderable(bottomTexture);
    }

    private ResourceLocation getPlayerSkinTexture() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return null;
        }
        PlayerInfo playerInfo = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        if (playerInfo == null) {
            return null;
        }
        return playerInfo.getSkin().texture();
    }

    private String formatPlayTime(long ticks) {
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return Component.translatable("screen." + SRE.MOD_ID + ".player_stats.time.days_hours_minutes", days,
                    hours % 24, minutes % 60).getString();
        } else if (hours > 0) {
            return Component
                    .translatable("screen." + SRE.MOD_ID + ".player_stats.time.hours_minutes", hours, minutes % 60)
                    .getString();
        } else if (minutes > 0) {
            return Component
                    .translatable("screen." + SRE.MOD_ID + ".player_stats.time.minutes_seconds", minutes, seconds % 60)
                    .getString();
        } else {
            return Component.translatable("screen." + SRE.MOD_ID + ".player_stats.time.seconds", seconds).getString();
        }
    }

    private double getKdRatio(int kills, int deaths) {
        if (deaths == 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    private double getWinRate(int wins, int gamesPlayed) {
        if (gamesPlayed == 0) {
            return 0.0;
        }
        return (double) wins / gamesPlayed * 100.0;
    }

    /**
     * 绘制滚动条
     */
    private void drawScrollbar(GuiGraphics context, int mouseX, int mouseY) {
        if (maxScroll <= 0)
            return; // 内容不需要滚动时不显示滚动条

        int scrollbarX = getX() + width - SCROLLBAR_WIDTH;
        int scrollbarY = getY();
        int scrollbarHeight = height;

        // 绘制轨道
        context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight,
                SCROLLBAR_COLOR);

        // 计算滑块高度和位置
        float contentRatio = (float) height / (height + maxScroll);
        int thumbHeight = (int) (contentRatio * scrollbarHeight);
        thumbHeight = Mth.clamp(thumbHeight, 10, scrollbarHeight); // 滑块最小高度 10px

        int thumbY = scrollbarY + (int) ((float) scrollY / maxScroll * (scrollbarHeight - thumbHeight));

        // 绘制滑块
        context.fill(scrollbarX, thumbY, scrollbarX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先判断点击是否在滚动条上
        if (isMouseOverScrollbar(mouseX, mouseY)) {
            // 计算滑块位置，判断是否点击了滑块
            int scrollbarX = getX() + width - SCROLLBAR_WIDTH;
            int scrollbarY = getY();
            int scrollbarHeight = height;
            float contentRatio = (float) height / (height + maxScroll);
            int thumbHeight = (int) (contentRatio * scrollbarHeight);
            thumbHeight = Mth.clamp(thumbHeight, 10, scrollbarHeight);
            int thumbY = scrollbarY + (int) ((float) scrollY / maxScroll * (scrollbarHeight - thumbHeight));

            if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                // 点击了滑块，开始拖动
                isDraggingScrollbar = true;
                scrollbarClickedY = (int) (mouseY - thumbY);
                return true;
            } else {
                // 点击在轨道上，跳转到对应位置
                double clickRelative = (mouseY - scrollbarY) / scrollbarHeight;
                setScrollY((int) (clickRelative * maxScroll));
                return true;
            }
        }

        // 否则将点击事件转发给子组件（坐标需减去容器偏移并加上滚动偏移）
        double childMouseX = mouseX - getX();
        double childMouseY = mouseY - getY() + scrollY;

        for (var child : children) {
            if (child.mouseClicked(childMouseX + child.getRectangle().left(), childMouseY + child.getRectangle().top(),
                    button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }

        // 转发给子组件
        double childMouseX = mouseX - getX();
        double childMouseY = mouseY - getY() + scrollY;
        for (var child : children) {
            if (child.mouseReleased(childMouseX + child.getRectangle().left(), childMouseY + child.getRectangle().top(),
                    button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar) {
            // 根据鼠标拖动更新滚动偏移
            int scrollbarY = getY();
            int scrollbarHeight = height;
            float contentRatio = (float) height / (height + maxScroll);
            int thumbHeight = (int) (contentRatio * scrollbarHeight);
            thumbHeight = Mth.clamp(thumbHeight, 10, scrollbarHeight);

            double newThumbTop = mouseY - scrollbarY - scrollbarClickedY;
            double newScrollRatio = newThumbTop / (scrollbarHeight - thumbHeight);
            newScrollRatio = Mth.clamp(newScrollRatio, 0, 1);
            setScrollY((int) (newScrollRatio * maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 处理鼠标滚轮滚动，verticalAmount 通常为滚动距离（正数向上，负数向下）
        if (isMouseOver(mouseX, mouseY)) {
            int delta = (int) (-verticalAmount * 10); // 每次滚动10像素，方向取反以符合直觉
            setScrollY(scrollY + delta);
            return true;
        }
        return false;
    }

    private void setScrollY(int newScroll) {
        scrollY = Mth.clamp(newScroll, 0, maxScroll);
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        int scrollbarX = getX() + width - SCROLLBAR_WIDTH;
        return mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH
                && mouseY >= getY() && mouseY <= getY() + height;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // 判断鼠标是否在容器区域内（包括滚动条区域）
        return mouseX >= getX() && mouseX <= getX() + width
                && mouseY >= getY() && mouseY <= getY() + height;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 绘制面板背景（可选）
        // graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
        // 0x40000000);
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        int leftPanelX = getX();
        int leftPanelWidth = getWidth();
        int currentY = getY() - this.scrollY;
        int leftColumnX = leftPanelX + 10;

        // 玩家名称（如果没有头部）
        ResourceLocation skinTexture = getPlayerSkinTexture();
        if (skinTexture == null && Minecraft.getInstance().player != null) {
            String playerName = Minecraft.getInstance().player.getDisplayName().getString();
            int playerNameWidth = Minecraft.getInstance().font.width(playerName);
            int centeredX = leftColumnX + (leftPanelWidth - 20 - playerNameWidth) / 2; // 考虑左右10像素边距
            graphics.drawString(Minecraft.getInstance().font,
                    Minecraft.getInstance().player.getDisplayName().copy()
                            .withStyle(style -> style.withColor(0xFFFFA0).withBold(true)),
                    centeredX, currentY, 0xFFFFA0);
            currentY += Minecraft.getInstance().font.lineHeight + 15;
        } else if (skinTexture != null) {
            currentY += 32 + 15;
        }

        // 通用统计数据标题
        String statsTitle = Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats").getString();
        int titleWidth = Minecraft.getInstance().font.width(statsTitle);
        int centeredTitleX = leftColumnX + (leftPanelWidth - 20 - titleWidth) / 2;
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats")
                        .withStyle(style -> style.withBold(true)),
                centeredTitleX, currentY, 0xFFFFFFFF);
        currentY += Minecraft.getInstance().font.lineHeight + 10;

        // 两列布局
        int columnWidth = (leftPanelWidth - 2 * 10) / 2 - 5;
        int rightColumnX = leftColumnX + columnWidth + 10;
        int columnStartY = currentY;

        // 左列数据
        drawStatLabelCentered(graphics, leftColumnX, columnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_play_time", formatPlayTime(stats.getTotalPlayTime()),
                columnWidth);
        columnStartY += 20;
        drawStatLabelCentered(graphics, leftColumnX, columnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_games_played",
                String.valueOf(stats.getTotalGamesPlayed()), columnWidth);
        columnStartY += 20;
        drawStatLabelCentered(graphics, leftColumnX, columnStartY, "screen." + SRE.MOD_ID + ".player_stats.total_kills",
                String.valueOf(stats.getTotalKills()), columnWidth);
        columnStartY += 20;
        drawStatLabelCentered(graphics, leftColumnX, columnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_team_kills", String.valueOf(stats.getTotalTeamKills()),
                columnWidth);
        columnStartY += 20;
        drawStatLabelCentered(graphics, leftColumnX, columnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_deaths", String.valueOf(stats.getTotalDeaths()),
                columnWidth);
        columnStartY += 20;
        drawStatLabelCentered(graphics, leftColumnX, columnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_lovers_wins", String.valueOf(stats.getTotalLoversWins()),
                columnWidth);
        columnStartY += 20;

        // 右列数据
        int rightColumnStartY = currentY;
        drawStatLabelCentered(graphics, rightColumnX, rightColumnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_wins", String.valueOf(stats.getTotalWins()), columnWidth);
        rightColumnStartY += 20;
        drawStatLabelCentered(graphics, rightColumnX, rightColumnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.total_losses", String.valueOf(stats.getTotalLosses()),
                columnWidth);
        rightColumnStartY += 20;
        drawStatLabelCentered(graphics, rightColumnX, rightColumnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.win_rate",
                String.format("%.2f%%", getWinRate(stats.getTotalWins(), stats.getTotalGamesPlayed())), columnWidth);
        rightColumnStartY += 20;
        drawStatLabelCentered(graphics, rightColumnX, rightColumnStartY,
                "screen." + SRE.MOD_ID + ".player_stats.kd_ratio",
                String.format("%.2f", getKdRatio(stats.getTotalKills(), stats.getTotalDeaths())), columnWidth);
        rightColumnStartY += 20;

        // 阵营统计部分（从左列结束位置开始）
        currentY = columnStartY;
        currentY += 10;
        drawFactionStats(graphics, leftColumnX, currentY, leftPanelWidth - 20);

        // 渲染子组件（头部和底部贴图）
        for (Renderable renderable : renderables) {
            renderable.render(graphics, mouseX, mouseY, delta);
        }
        graphics.disableScissor();
        // 绘制滚动条（在内容之上）
        drawScrollbar(graphics, mouseX, mouseY);
    }

    private void drawFactionStats(GuiGraphics graphics, int x, int y, int width) {
        // 平民阵营统计
        drawFactionSection(graphics, x, y, width,
                "screen." + SRE.MOD_ID + ".player_stats.civilian_stats",
                "screen." + SRE.MOD_ID + ".player_stats.total_civilian_games",
                "screen." + SRE.MOD_ID + ".player_stats.total_civilian_wins",
                "screen." + SRE.MOD_ID + ".player_stats.civilian_win_rate",
                "screen." + SRE.MOD_ID + ".player_stats.civilian_kd_ratio",
                stats.getTotalCivilianGames(), stats.getTotalCivilianWins(),
                stats.getTotalCivilianKills(), stats.getTotalCivilianDeaths());

        y += 50;

        // 杀手阵营统计
        drawFactionSection(graphics, x, y, width,
                "screen." + SRE.MOD_ID + ".player_stats.killer_stats",
                "screen." + SRE.MOD_ID + ".player_stats.total_killer_games",
                "screen." + SRE.MOD_ID + ".player_stats.total_killer_wins",
                "screen." + SRE.MOD_ID + ".player_stats.killer_win_rate",
                "screen." + SRE.MOD_ID + ".player_stats.killer_kd_ratio",
                stats.getTotalKillerGames(), stats.getTotalKillerWins(),
                stats.getTotalKillerKills(), stats.getTotalKillerDeaths());

        y += 50;

        // 中立阵营统计
        drawFactionSection(graphics, x, y, width,
                "screen." + SRE.MOD_ID + ".player_stats.neutral_stats",
                "screen." + SRE.MOD_ID + ".player_stats.total_neutral_games",
                "screen." + SRE.MOD_ID + ".player_stats.total_neutral_wins",
                "screen." + SRE.MOD_ID + ".player_stats.neutral_win_rate",
                "screen." + SRE.MOD_ID + ".player_stats.neutral_kd_ratio",
                stats.getTotalNeutralGames(), stats.getTotalNeutralWins(),
                stats.getTotalNeutralKills(), stats.getTotalNeutralDeaths());

        y += 50;

        // 警长阵营统计
        drawFactionSection(graphics, x, y, width,
                "screen." + SRE.MOD_ID + ".player_stats.sheriff_stats",
                "screen." + SRE.MOD_ID + ".player_stats.total_sheriff_games",
                "screen." + SRE.MOD_ID + ".player_stats.total_sheriff_wins",
                "screen." + SRE.MOD_ID + ".player_stats.sheriff_win_rate",
                "screen." + SRE.MOD_ID + ".player_stats.sheriff_kd_ratio",
                stats.getTotalSheriffGames(), stats.getTotalSheriffWins(),
                stats.getTotalSheriffKills(), stats.getTotalSheriffDeaths());
    }

    private void drawFactionSection(GuiGraphics graphics, int x, int y, int width,
            String titleKey, String gamesKey, String winsKey, String winRateKey, String kdRatioKey,
            int games, int wins, int kills, int deaths) {
        // 绘制标题
        String title = Component.translatable(titleKey).getString();
        int titleWidth = Minecraft.getInstance().font.width(title);
        int titleX = x + (width - titleWidth) / 2;
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable(titleKey).withStyle(style -> style.withColor(0xFFFFFF).withBold(true)),
                titleX, y, 0xFFFFFF);

        // 两列布局
        int columnWidth = width / 2 - 5;
        int rightColumnX = x + columnWidth + 10;
        int lineY = y + 15;

        // 左列：场次和胜场
        drawStatLabelCentered(graphics, x, lineY, gamesKey, String.valueOf(games), columnWidth);
        lineY += 12;
        drawStatLabelCentered(graphics, x, lineY, winsKey, String.valueOf(wins), columnWidth);

        // 右列：胜率和K/D比
        lineY = y + 15;
        drawStatLabelCentered(graphics, rightColumnX, lineY, winRateKey,
                String.format("%.2f%%", getWinRate(wins, games)), columnWidth);
        lineY += 12;
        drawStatLabelCentered(graphics, rightColumnX, lineY, kdRatioKey,
                String.format("%.2f", getKdRatio(kills, deaths)), columnWidth);
    }

    @Override
    protected void updateWidgetNarration(
            net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
        // 无需 narration
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    private void drawStatLabel(GuiGraphics graphics, int x, int y, String translationKey, String value) {
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable(translationKey, value).withStyle(style -> style.withColor(0xFFCCCCCC)),
                x, y, 0xFFCCCCCC);
    }

    private void drawStatLabelCentered(GuiGraphics graphics, int x, int y, String translationKey, String value,
            int columnWidth) {
        String text = Component.translatable(translationKey, value).getString();
        int textWidth = Minecraft.getInstance().font.width(text);
        int centeredX = x + (columnWidth - textWidth) / 2;
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable(translationKey, value).withStyle(style -> style.withColor(0xFFCCCCCC)),
                centeredX, y, 0xFFCCCCCC);
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    /**
     * 渲染玩家头部的自定义组件
     */
    private static class PlayerHeadComponent extends AbstractWidget {
        private final ResourceLocation skinTexture;
        private final int size;

        public PlayerHeadComponent(int x, int y, int size, ResourceLocation skinTexture) {
            super(x, y, size, size, Component.empty());
            this.skinTexture = skinTexture;
            this.size = size;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (skinTexture == null)
                return;

            RenderSystem.enableBlend();
            graphics.pose().pushPose();
            graphics.pose().translate(getX(), getY(), 0);
            // 渲染头部（8x8 纹理区域，位于 8,8 到 16,16）
            graphics.blit(skinTexture, 0, 0, size, size, 8, 8, 8, 8, 64, 64);
            // 渲染头盔层（40,8 到 48,16）
            graphics.blit(skinTexture, 0, 0, size, size, 40, 8, 8, 8, 64, 64);
            graphics.pose().popPose();
            RenderSystem.disableBlend();
        }

        @Override
        protected void updateWidgetNarration(
                net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
            // 无需 narration
        }
    }

    /**
     * 渲染底部贴图的自定义组件
     */
    private static class BottomTextureComponent extends AbstractWidget {
        private static final ResourceLocation TEXTURE = PlayerStatsScreen.ID; // 使用 PlayerStatsScreen 的 ID

        public BottomTextureComponent(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            if (TEXTURE == null)
                return;
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0);
            int height = 254;
            int width = 497;
            float scale = 0.28f;
            graphics.pose().scale(scale, scale, 1f);
            int xOffset = 0;
            int yOffset = 0;
            graphics.innerBlit(TEXTURE, (int) (xOffset - width / 2f), (int) (xOffset + width / 2f),
                    (int) (yOffset - height / 2f), (int) (yOffset + height / 2f), 0, 0, 1f, 0, 1f, 1f, 1f, 1f, 1f);
            graphics.pose().popPose();

            RenderSystem.disableBlend();
        }

        @Override
        protected void updateWidgetNarration(
                net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
            // 无需 narration
        }
    }
}