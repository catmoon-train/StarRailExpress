package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.OpenShelterPanelS2CPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 避难所控制面板（右键 {@code sixty_seconds_shelter_panel} 方块打开）：本队仪表盘——
 * 家门耐久/等级、电力剩余（本地倒计时）、已解锁科技、成员健康与状态。
 * 风格遵循 {@code docs/ui_style.md}。快照式数据，重开刷新。
 */
public class ShelterPanelScreen extends Screen {
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int BLUE = 0xFF5EB7D8;

    private final OpenShelterPanelS2CPacket data;
    private final long powerEndGameTime;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public ShelterPanelScreen(OpenShelterPanelS2CPacket data) {
        super(Component.translatable("gui.noellesroles.sixty_seconds.shelter_panel_title"));
        this.data = data;
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        this.powerEndGameTime = now + Math.max(0, data.powerRemainingTicks());
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.7F, 320, 430);
        panelH = (int) Mth.clamp(this.height * 0.8F, 240, 340);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 40, panelY + panelH - 28, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECO);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        int x = panelX + 14;
        int colW = (panelW - 28) / 2;

        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelY + 12, GOLD);

        int y = panelY + 30;

        // ── 左列：家门 + 电力 ─────────────────────────────
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_door")
                .withStyle(ChatFormatting.BOLD), x, y, GOLD);
        int doorY = y + 12;
        // 门耐久条
        g.fill(x, doorY, x + colW - 10, doorY + 9, 0xFF1B2129);
        double doorRatio = data.doorMaxHp() <= 0 ? 0 : Mth.clamp(data.doorHp() / (double) data.doorMaxHp(), 0, 1);
        int doorFill = (int) ((colW - 10) * doorRatio);
        int doorColor = data.doorBroken() ? RED : (doorRatio > 0.5 ? GREEN : (doorRatio > 0.25 ? GOLD : RED));
        if (doorFill > 0) {
            g.fill(x, doorY, x + doorFill, doorY + 9, doorColor);
        }
        g.drawString(this.font, data.doorHp() + "/" + data.doorMaxHp(), x + colW - 4, doorY, TEXT);
        Component doorStatus = data.doorBroken()
                ? Component.translatable("gui.noellesroles.sixty_seconds.panel_door_broken")
                : Component.translatable("gui.noellesroles.sixty_seconds.panel_door_level", data.doorLevel());
        g.drawString(this.font, doorStatus, x, doorY + 13, data.doorBroken() ? RED : TEXT);

        // 电力
        int powerY = doorY + 30;
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_power")
                .withStyle(ChatFormatting.BOLD), x, powerY, GOLD);
        long remaining = mc.level == null ? 0 : Math.max(0, powerEndGameTime - mc.level.getGameTime());
        long seconds = remaining / 20;
        Component power = remaining > 0
                ? Component.translatable("gui.noellesroles.sixty_seconds.power_on",
                        String.format("%02d:%02d", seconds / 60, seconds % 60))
                : Component.translatable("gui.noellesroles.sixty_seconds.power_off");
        g.drawString(this.font, power, x, powerY + 12, remaining > 0 ? GREEN : RED);

        // 库存物资
        int supplyY = powerY + 28;
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_supplies",
                data.suppliesCount()), x, supplyY, BLUE);

        // ── 右列：科技 + 成员 ─────────────────────────────
        int rx = panelX + 14 + colW + 10;
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_tech",
                data.techIds().size()).withStyle(ChatFormatting.BOLD), rx, y, GOLD);
        int techY = y + 12;
        int shown = 0;
        for (String id : data.techIds()) {
            if (shown >= 6) {
                g.drawString(this.font, "…", rx, techY, MUTED);
                techY += 10;
                break;
            }
            g.drawString(this.font, Component.translatable("tech.noellesroles.sixty_seconds." + id),
                    rx, techY, TEXT);
            techY += 10;
            shown++;
        }
        if (data.techIds().isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_tech_none"),
                    rx, techY, MUTED);
            techY += 10;
        }

        // 成员
        int memberY = Math.max(techY + 8, y + 90);
        g.drawString(this.font, Component.translatable("gui.noellesroles.sixty_seconds.panel_members")
                .withStyle(ChatFormatting.BOLD), rx, memberY, GOLD);
        memberY += 12;
        for (int i = 0; i < data.memberNames().size(); i++) {
            int flags = data.memberFlags().get(i);
            boolean sick = (flags & 1) != 0;
            boolean downed = (flags & 2) != 0;
            boolean monster = (flags & 4) != 0;
            int health = data.memberHealth().get(i);
            int nameColor = monster ? 0xFFB06AE6 : (downed ? RED : (sick ? GOLD : TEXT));
            g.drawString(this.font, data.memberNames().get(i), rx, memberY, nameColor);
            String state = monster ? "☠" : downed ? "✚" : sick ? "✱" : String.valueOf(health);
            int stateColor = monster ? 0xFFB06AE6 : downed ? RED : sick ? GOLD
                    : (health > 50 ? GREEN : health > 25 ? GOLD : RED);
            int sw = this.font.width(state);
            g.drawString(this.font, state, panelX + panelW - 14 - sw, memberY, stateColor);
            memberY += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
