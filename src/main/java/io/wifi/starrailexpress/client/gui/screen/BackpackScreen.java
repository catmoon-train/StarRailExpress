package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.backpack.BackpackState;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 场外背包 GUI：展示各阵营卡牌计数，点击非空卡可激活（沿用 {@code sre:pass activate} 路径）。
 * 视觉风格参考 {@link MapIntroduceScreen}：暖金色描边 + 深棕渐变面板 + 手绘列表行。
 * 数据来自 {@link ClientPlayerDataCache#backpack}（由服务端 BackpackManager 通过通用 part 同步）。
 */
public class BackpackScreen extends Screen {

    // ---- 配色（与 MapIntroduceScreen 一致的暖金/深棕主题）----
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GOLD = 0xFFD4AF37;
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int OUTLINE = 0xFF8B6914;

    /** 渲染顺序与各阵营卡按钮的强调色（与通行证界面保持一致，紫色为杀手中立）。 */
    private static final FactionCardType[] DISPLAY_ORDER = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER };
    private static final int[] ACCENT = { 0xFFC75450, 0xFF5EB7D8, 0xFFE0AD5B, 0xFFB18AE6 };

    private static final int PAD = 8;
    private static final int HEADER_H = 34;
    private static final int ROW_H = 38;
    private static final int ROW_GAP = 5;
    private static final int FOOTER_H = 22;

    private final LocalPlayer player;
    private BackpackState backpack;
    private Screen parent;

    private int panelX, panelY, panelW, panelH;
    private int rowX, rowW;

    public BackpackScreen(Screen parent) {
        super(Component.translatable("sre.backpack.title"));
        this.player = Minecraft.getInstance().player;
        this.backpack = ClientPlayerDataCache.backpack(player.getUUID());
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.backpack = ClientPlayerDataCache.backpack(player.getUUID());
        computeLayout();
    }

    private void computeLayout() {
        panelW = Mth.clamp((int) (width * 0.55F), 260, 360);
        panelH = PAD * 2 + HEADER_H + DISPLAY_ORDER.length * (ROW_H + ROW_GAP) - ROW_GAP + FOOTER_H;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        rowX = panelX + PAD;
        rowW = panelW - PAD * 2;
    }

    private int rowY(int index) {
        return panelY + PAD + HEADER_H + index * (ROW_H + ROW_GAP);
    }

    private int count(FactionCardType type) {
        return backpack.cards.getOrDefault(type, 0);
    }

    // =========================================================================
    // 渲染
    // =========================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        
        computeLayout();

        drawPanelBg(g, panelX, panelY, panelW, panelH);

        // ---- 顶部渐隐 + 标题 ----
        g.fillGradient(0, 0, width, panelY - 4, 0xBB000000, 0x00000000);
        g.drawCenteredString(font, title, panelX + panelW / 2, panelY + PAD + 4, 0xF5E8C8);
        int total = 0;
        for (FactionCardType type : DISPLAY_ORDER) {
            total += count(type);
        }
        Component subtitle = Component.translatable("sre.backpack.subtitle", total).withStyle(ChatFormatting.GRAY);
        g.drawCenteredString(font, subtitle, panelX + panelW / 2, panelY + PAD + 18, MUTED);
        g.fill(panelX + PAD, panelY + PAD + HEADER_H - 4, panelX + panelW - PAD, panelY + PAD + HEADER_H - 3, 0x33FFE8C0);

        // ---- 卡牌行 ----
        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            renderCardRow(g, i, mouseX, mouseY);
        }

        // ---- 底部提示 ----
        g.drawCenteredString(font, Component.translatable("sre.backpack.hint").withStyle(ChatFormatting.GRAY),
                panelX + panelW / 2, panelY + panelH - FOOTER_H + 6, MUTED);

    }

    private void renderCardRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        FactionCardType type = DISPLAY_ORDER[index];
        int accent = ACCENT[index];
        int count = count(type);
        boolean owned = count > 0;
        int x = rowX;
        int y = rowY(index);
        boolean hovered = owned && inside(mouseX, mouseY, x, y, rowW, ROW_H);

        // 行背景：拥有时金色渐变，悬停更亮；空卡暗淡
        if (hovered) {
            g.fillGradient(x, y, x + rowW, y + ROW_H,
                    blendColors(PANEL_TOP, accent, 0.42F), blendColors(PANEL_BOTTOM, accent, 0.24F));
        } else if (owned) {
            g.fillGradient(x, y, x + rowW, y + ROW_H,
                    blendColors(PANEL_TOP, accent, 0.22F), blendColors(PANEL_BOTTOM, accent, 0.10F));
        } else {
            g.fillGradient(x, y, x + rowW, y + ROW_H, 0x66120A04, 0x66120A04);
        }
        g.renderOutline(x, y, rowW, ROW_H, hovered ? GOLD : (owned ? 0xFF5A4530 : 0xFF3A2C1E));
        // 左侧阵营色条
        g.fill(x + 1, y + 1, x + 4, y + ROW_H - 1, owned ? accent : (accent & 0x66FFFFFF));

        // 卡名 + 状态副行
        Component name = Component.translatable("sre.pass.faction." + type.questKey);
        g.drawString(font, name, x + 12, y + 8, owned ? TEXT : MUTED, false);
        Component hint = Component.translatable(owned ? "sre.backpack.click_activate" : "sre.backpack.unavailable");
        g.drawString(font, hint, x + 12, y + 22, owned ? 0xFFB8C9A8 : 0xFF6A5A48, false);

        // 右侧计数
        String countText = "×" + count;
        int cw = font.width(countText);
        g.drawString(font, countText, x + rowW - 14 - cw, y + 14, owned ? (accent | 0xFF000000) : MUTED, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xC0050302, 0xE00C0805);
    }

    // =========================================================================
    // 输入
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < DISPLAY_ORDER.length; i++) {
                FactionCardType type = DISPLAY_ORDER[i];
                if (count(type) > 0 && inside(mouseX, mouseY, rowX, rowY(i), rowW, ROW_H)) {
                    sendCommand("sre:pass activate " + type.questKey);
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    // =========================================================================
    // 工具
    // =========================================================================

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void drawPanelBg(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(x, y, w, h, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFE8C0);
    }

    private static int blendColors(int c1, int c2, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int a1 = c1 >>> 24, r1 = c1 >> 16 & 255, g1 = c1 >> 8 & 255, b1 = c1 & 255;
        int a2 = c2 >>> 24, r2 = c2 >> 16 & 255, g2 = c2 >> 8 & 255, b2 = c2 & 255;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int gg = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return a << 24 | r << 16 | gg << 8 | b;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
