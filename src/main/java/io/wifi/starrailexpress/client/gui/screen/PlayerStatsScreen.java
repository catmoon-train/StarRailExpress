// PlayerStatsScreen.java (重写后)
package io.wifi.starrailexpress.client.gui.screen;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;

public class PlayerStatsScreen extends Screen {
    private SREPlayerStatsComponent stats;
    private final UUID targetPlayerUuid;
    private GeneralStatsPanel generalStatsPanel;
    private RoleStatsPanel roleStatsPanel;

    private static final int GENERAL_STATS_VIEW = 0;
    private static final int ROLE_STATS_VIEW = 1;
    private int currentView = GENERAL_STATS_VIEW;

    public static final @NotNull ResourceLocation ID = SRE.watheId("textures/gui/game.png");

    // 分类标签相关
    private int tabX, tabY, tabWidth, tabHeight;
    private static final int TAB_W = 100;
    private static final int TAB_H = 22;

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
        // 计算标签位置
        tabX = (width - TAB_W * 2 - 10) / 2;
        tabY = 38;
        tabWidth = TAB_W;
        tabHeight = TAB_H;

        int panelWidth = (int) (width * 0.6);
        int panelHeight = (int) (height * 0.7);
        int panelX = (width - panelWidth) / 2;
        int panelY = tabY + tabHeight + 10;

        generalStatsPanel = new GeneralStatsPanel(
                panelX, panelY, panelWidth, panelHeight - (panelY - (tabY + tabHeight + 10)),
                stats, width, height
        );
        generalStatsPanel.init();
        addRenderableWidget(generalStatsPanel);

        roleStatsPanel = new RoleStatsPanel(
                panelX, panelY, panelWidth, panelHeight - (panelY - (tabY + tabHeight + 10)),
                stats
        );
        addRenderableWidget(roleStatsPanel);

        switchView(currentView);
    }

    private void switchView(int view) {
        currentView = view;
        generalStatsPanel.setVisible(view == GENERAL_STATS_VIEW);
        roleStatsPanel.setVisible(view == ROLE_STATS_VIEW);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // 背景渐变
        graphics.fillGradient(0, 0, width, height, 0xC0102030, 0xD0081018);
        super.render(graphics, mouseX, mouseY, delta);
        renderTabBar(graphics, mouseX, mouseY);
        // 标题
        graphics.drawCenteredString(font, title, width / 2, 16, 0xEEEEFF);
    }

    private void renderTabBar(GuiGraphics g, int mx, int my) {
        int[] tabColors = {0xFF5577CC, 0xFFAA44CC}; // 通用 / 角色
        String[] labels = {
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats_button").getString(),
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.role_stats_button").getString()
        };

        for (int i = 0; i < 2; i++) {
            int x = tabX + i * (tabWidth + 10);
            boolean active = (i == currentView);
            boolean hovered = !active && mx >= x && mx <= x + tabWidth && my >= tabY && my <= tabY + tabHeight;
            int baseColor = tabColors[i];

            // 背景
            if (active) {
                g.fillGradient(x, tabY, x + tabWidth, tabY + tabHeight,
                        blendColors(0xFF0D1020, baseColor, 0.50f),
                        blendColors(0xFF0A0C18, baseColor, 0.28f));
                g.fill(x, tabY + tabHeight - 2, x + tabWidth, tabY + tabHeight, baseColor);
                g.fill(x, tabY, x + 1, tabY + tabHeight, (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(x + tabWidth - 1, tabY, x + tabWidth, tabY + tabHeight, (baseColor & 0x00FFFFFF) | 0xAA000000);
                g.fill(x + 1, tabY, x + tabWidth - 1, tabY + 1, (baseColor & 0x00FFFFFF) | 0x55000000);
            } else if (hovered) {
                g.fillGradient(x, tabY, x + tabWidth, tabY + tabHeight,
                        blendColors(0xFF0D1020, baseColor, 0.22f),
                        blendColors(0xFF0A0C18, baseColor, 0.10f));
                g.fill(x, tabY + tabHeight - 1, x + tabWidth, tabY + tabHeight, (baseColor & 0x00FFFFFF) | 0x66000000);
                g.renderOutline(x, tabY, tabWidth, tabHeight, (baseColor & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(x, tabY, x + tabWidth, tabY + tabHeight, 0x33111828);
                g.renderOutline(x, tabY, tabWidth, tabHeight, 0x33334466);
            }

            String truncated = font.plainSubstrByWidth(labels[i], tabWidth - 8);
            int textColor = active ? (baseColor | 0xFF000000) : hovered ? 0xFFCCDDFF : 0xFF7788AA;
            g.drawCenteredString(font, truncated, x + tabWidth / 2, tabY + (tabHeight - font.lineHeight) / 2, textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 2; i++) {
            int x = tabX + i * (tabWidth + 10);
            if (mx >= x && mx <= x + tabWidth && my >= tabY && my <= tabY + tabHeight) {
                switchView(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == Minecraft.getInstance().options.keyInventory.hashCode()||
                keyCode == SREClient.statsKeybind.hashCode()) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {}

    public int getCurrentView() { return currentView; }
    public UUID getTargetPlayerUuid() { return targetPlayerUuid; }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f) return c1;
        if (t >= 1f) return c2;
        int r = (int)(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int)(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int)((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}