package io.wifi.starrailexpress.client.gui.screen;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
 
public class PlayerStatsScreen extends Screen {
    private SREPlayerStatsComponent stats;
    private final UUID targetPlayerUuid;
    private GeneralStatsPanel generalStatsPanel;
    private RoleStatsPanel roleStatsPanel;
    private Button generalStatsButton;
    private Button roleStatsButton;
 
    private static final int GENERAL_STATS_VIEW = 0;
    private static final int ROLE_STATS_VIEW = 1;
    private int currentView = GENERAL_STATS_VIEW;
 
    public static final @NotNull ResourceLocation ID = SRE.watheId("textures/gui/game.png");
    
    public PlayerStatsScreen(UUID targetPlayerUuid) {
        super(Component.translatable("screen." + SRE.MOD_ID + ".player_stats.title"));
        this.targetPlayerUuid = targetPlayerUuid;
        Player targetPlayer = Minecraft.getInstance().level.getPlayerByUUID(targetPlayerUuid);
        if (targetPlayer != null) {
            this.stats = SREPlayerStatsComponent.KEY.get(targetPlayer);
        }
    }

    @Override
    protected void init() {
        super.init();
        int screenWidth = this.width;
        int screenHeight = this.height;
        int panelWidth = (int) (screenWidth * 0.6);
        int panelHeight = (int) (screenHeight * 0.7);
        int panelX = (screenWidth - panelWidth) / 2;
        int panelY = (screenHeight - panelHeight) / 2;

        // 切换按钮
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonSpacing = 10;
        int totalButtonWidth = (buttonWidth * 2) + buttonSpacing;
        int buttonX = (screenWidth - totalButtonWidth) / 2;
        int buttonY = panelY + 10;

        generalStatsButton = Button.builder(
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats_button"),
                (button) -> this.switchView(GENERAL_STATS_VIEW)
        ).pos(buttonX, buttonY).size(buttonWidth, buttonHeight).build();
        this.addRenderableWidget(generalStatsButton);

        roleStatsButton = Button.builder(
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.role_stats_button"),
                (button) -> this.switchView(ROLE_STATS_VIEW)
        ).pos(buttonX + buttonWidth + buttonSpacing, buttonY).size(buttonWidth, buttonHeight).build();
        this.addRenderableWidget(roleStatsButton);

        // 初始化面板
        generalStatsPanel = new GeneralStatsPanel(
                panelX,
                buttonY + buttonHeight + 10,
                panelWidth,
                panelHeight - buttonHeight - 10,
                stats,
                screenWidth,
                screenHeight
        );
        generalStatsPanel.init();
        this.addRenderableWidget(generalStatsPanel);

        roleStatsPanel = new RoleStatsPanel(
                panelX,
                buttonY + buttonHeight + 10,
                panelWidth,
                panelHeight - buttonHeight - 10,
                stats
        );
        this.addRenderableWidget(roleStatsPanel);

        // 默认显示通用统计
        switchView(GENERAL_STATS_VIEW);
    }

    private void switchView(int view) {
        this.currentView = view;
        generalStatsPanel.setVisible(view == GENERAL_STATS_VIEW);
        roleStatsPanel.setVisible(view == ROLE_STATS_VIEW);

        generalStatsButton.active = (view != GENERAL_STATS_VIEW);
        roleStatsButton.active = (view != ROLE_STATS_VIEW);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 渲染背景（半透明黑色）
        graphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        // 渲染标题
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, (int)(32), 0xFFFFFFFF);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == Minecraft.getInstance().options.keyInventory.getDefaultKey().getValue() || keyCode == SREClient.statsKeybind.getDefaultKey().getValue()) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        // 屏幕 tick
    }

    public int getCurrentView() {
        return currentView;
    }

    public UUID getTargetPlayerUuid() {
        return targetPlayerUuid;
    }
}