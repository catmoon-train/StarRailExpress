// PlayerStatsScreen.java
package io.wifi.starrailexpress.client.gui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

public class PlayerStatsScreen extends Screen {

    private SREPlayerStatsComponent stats;
    private final UUID targetPlayerUuid;
    private GeneralStatsPanel generalStatsPanel;
    private RoleStatsPanel roleStatsPanel;
    // 自己的子组件列表（不走 Screen 的 vanilla 列表，全手动路由）
    private final List<GuiEventListener> children = new ArrayList<>();

    private static final int GENERAL_STATS_VIEW = 0;
    private static final int ROLE_STATS_VIEW = 1;
    private int currentView = GENERAL_STATS_VIEW;

    public static final @NotNull ResourceLocation ID = SRE.watheId("textures/gui/game.png");

    private final List<Renderable> renderables = new ArrayList<>();
    private int tabX, tabY;
    private static final int TAB_W = 100;
    private static final int TAB_H = 22;
    /** 面板顶部到屏幕顶部留出的外边距 */
    private static final int PANEL_MARGIN_BOTTOM = 20;

    public PlayerStatsScreen(UUID targetPlayerUuid) {
        super(Component.translatable("screen." + SRE.MOD_ID + ".player_stats.title"));
        this.targetPlayerUuid = targetPlayerUuid;
        Player targetPlayer = Minecraft.getInstance().level.getPlayerByUUID(targetPlayerUuid);
        if (targetPlayer != null) {
            this.stats = SREPlayerStatsComponent.KEY.get(targetPlayer);
        }
    }

    private void addRenderableWidget2(Renderable r) {
        renderables.add(r);
        if (r instanceof GuiEventListener g)
            children.add(g);
    }

    @Override
    protected void init() {
        super.init();
        renderables.clear();
        children.clear();

        tabX = (width - TAB_W * 2 - 10) / 2;
        tabY = 38;

        int panelX = width <= 500 ? 30 : (int) (width * 0.2);
        int panelW = width - panelX * 2;
        int panelY = tabY + TAB_H + 10;
        // Fix: 面板高度 = 屏幕高度 - 面板起始Y - 底部边距，
        // 原代码写成 panelHeight - (panelY - ...) 化简等于 panelHeight = height*0.7，
        // 在小分辨率下面板底边会超出屏幕。
        int panelH = height - panelY - PANEL_MARGIN_BOTTOM;

        generalStatsPanel = new GeneralStatsPanel(panelX, panelY, panelW, panelH, stats, width, height);
        generalStatsPanel.init();
        addRenderableWidget2(generalStatsPanel);

        roleStatsPanel = new RoleStatsPanel(panelX, panelY, panelW, panelH, stats);
        addRenderableWidget2(roleStatsPanel);

        switchView(currentView);
    }

    public void playDownSound(SoundManager soundManager) {
        soundManager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void switchView(int view) {
        currentView = view;
        generalStatsPanel.setVisible(view == GENERAL_STATS_VIEW);
        roleStatsPanel.setVisible(view == ROLE_STATS_VIEW);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fillGradient(0, 0, width, height, 0xC0102030, 0xD0081018);
        super.render(graphics, mouseX, mouseY, delta);
        renderTabBar(graphics, mouseX, mouseY);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xEEEEFF);
        for (Renderable r : renderables)
            r.render(graphics, mouseX, mouseY, delta);
    }

    private void renderTabBar(GuiGraphics g, int mx, int my) {
        int[] tabColors = { 0xFF5577CC, 0xFFAA44CC };
        String[] labels = {
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.general_stats_button").getString(),
                Component.translatable("screen." + SRE.MOD_ID + ".player_stats.role_stats_button").getString()
        };

        for (int i = 0; i < 2; i++) {
            int x = tabX + i * (TAB_W + 10);
            boolean active = (i == currentView);
            boolean hovered = !active && mx >= x && mx <= x + TAB_W && my >= tabY && my <= tabY + TAB_H;
            int base = tabColors[i];

            if (active) {
                g.fillGradient(x, tabY, x + TAB_W, tabY + TAB_H,
                        blendColors(0xFF0D1020, base, 0.50f),
                        blendColors(0xFF0A0C18, base, 0.28f));
                g.fill(x, tabY + TAB_H - 2, x + TAB_W, tabY + TAB_H, base);
                g.fill(x, tabY, x + 1, tabY + TAB_H, (base & 0x00FFFFFF) | 0xAA000000);
                g.fill(x + TAB_W - 1, tabY, x + TAB_W, tabY + TAB_H, (base & 0x00FFFFFF) | 0xAA000000);
                g.fill(x + 1, tabY, x + TAB_W - 1, tabY + 1, (base & 0x00FFFFFF) | 0x55000000);
            } else if (hovered) {
                g.fillGradient(x, tabY, x + TAB_W, tabY + TAB_H,
                        blendColors(0xFF0D1020, base, 0.22f),
                        blendColors(0xFF0A0C18, base, 0.10f));
                g.fill(x, tabY + TAB_H - 1, x + TAB_W, tabY + TAB_H, (base & 0x00FFFFFF) | 0x66000000);
                g.renderOutline(x, tabY, TAB_W, TAB_H, (base & 0x00FFFFFF) | 0x44000000);
            } else {
                g.fill(x, tabY, x + TAB_W, tabY + TAB_H, 0x33111828);
                g.renderOutline(x, tabY, TAB_W, TAB_H, 0x33334466);
            }

            String truncated = font.plainSubstrByWidth(labels[i], TAB_W - 8);
            int textColor = active ? (base | 0xFF000000) : hovered ? 0xFFCCDDFF : 0xFF7788AA;
            g.drawCenteredString(font, truncated,
                    x + TAB_W / 2, tabY + (TAB_H - font.lineHeight) / 2, textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < 2; i++) {
            int x = tabX + i * (TAB_W + 10);
            if (mx >= x && mx <= x + TAB_W && my >= tabY && my <= tabY + TAB_H) {
                switchView(i);
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                return true;
            }
        }
        for (GuiEventListener child : children) {
            if (child.mouseClicked(mx, my, button))
                return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        for (GuiEventListener child : children)
            if (child.mouseReleased(mx, my, button))
                return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (GuiEventListener child : children)
            if (child.mouseScrolled(mx, my, sx, sy))
                return true;
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        for (GuiEventListener c : children)
            if (c.mouseDragged(mx, my, b, dx, dy))
                return true;
        return false;
    }

    @Override
    public boolean charTyped(char c, int m) {
        for (GuiEventListener child : children)
            if (child.charTyped(c, m))
                return true;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Fix: 原代码用 keyInventory.hashCode() 与 keyCode（int）比较，永远匹配不上。
        // 应使用 KeyMapping.matches(keyCode, scanCode) 做正确的按键匹配。

        if (super.keyPressed(keyCode, scanCode, modifiers))
            return true;
        for (GuiEventListener child : children)
            if (child.keyPressed(keyCode, scanCode, modifiers))
                return true;
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)
                || SREClient.statsKeybind.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
    }

    public int getCurrentView() {
        return currentView;
    }

    public UUID getTargetPlayerUuid() {
        return targetPlayerUuid;
    }

    private static int blendColors(int c1, int c2, float t) {
        if (t <= 0f)
            return c1;
        if (t >= 1f)
            return c2;
        int r = (int) (((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
        int g = (int) (((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}