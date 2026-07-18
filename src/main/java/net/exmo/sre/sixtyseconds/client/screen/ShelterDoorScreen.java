package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorMenu;
import net.exmo.sre.sixtyseconds.network.OpenShelterDoorS2CPacket;
import net.exmo.sre.sixtyseconds.network.ShelterDoorActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * 统一避难所门菜单（客户端）：展示服务端算好的操作选项（存物资 / 外出探索 / 返回住所 / 门外事件 / 拜访别队），
 * 点选后发 {@link ShelterDoorActionC2SPacket} 并关屏，由服务端重校验执行。
 * 本队门附带家门耐久/等级仪表；「返回住所」冷却在本地实时倒数。风格遵循 {@code docs/ui_style.md}。
 */
public class ShelterDoorScreen extends Screen {
    // ── ui_style 色板 ─────────────────────────────────────────
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int BLUE = 0xFF5EB7D8;
    private static final int ORANGE = 0xFFE0AD5B;
    private static final int PURPLE = 0xFFB18AE6;
    private static final int EDGE_IDLE = 0xFF5A4530;

    private static final int ROW_H = 32;
    private static final int ROW_GAP = 4;
    private static final int PAD = 12;

    private final OpenShelterDoorS2CPacket data;
    /** 「返回住所」冷却结束的本地 gameTime（本地倒数，免同步）。 */
    private final long returnReadyGameTime;
    private final float[] hoverAnim;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int rowsTop;

    public ShelterDoorScreen(OpenShelterDoorS2CPacket data) {
        super(Component.translatable("gui.noellesroles.sixty_seconds.door_title"));
        this.data = data;
        this.hoverAnim = new float[data.options().size()];
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        long ready = now;
        for (OpenShelterDoorS2CPacket.Option option : data.options()) {
            if (option.action() == SixtySecondsDoorMenu.ACTION_RETURN) {
                ready = now + option.param() * 20L;
            }
        }
        this.returnReadyGameTime = ready;
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.55F, 300, 380);
        int headerH = 32 + (data.ownDoor() ? 26 : 0);
        panelH = headerH + data.options().size() * (ROW_H + ROW_GAP) + 24;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        rowsTop = panelY + headerH;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECO_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelY + 12, GOLD);

        // ── 本队门：耐久条 + 等级 ──────────────────────────────
        if (data.ownDoor()) {
            int barX = panelX + PAD;
            int barW = panelW - PAD * 2;
            int barY = panelY + 28;
            g.fill(barX, barY, barX + barW, barY + 8, 0xFF1B2129);
            double ratio = data.doorMaxHp() <= 0 ? 0
                    : Mth.clamp(data.doorHp() / (double) data.doorMaxHp(), 0, 1);
            int fill = (int) (barW * ratio);
            int barColor = data.doorBroken() ? RED : (ratio > 0.5 ? GREEN : (ratio > 0.25 ? GOLD : RED));
            if (fill > 0) {
                g.fill(barX, barY, barX + fill, barY + 8, barColor);
            }
            Component status = data.doorBroken()
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_status_broken")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_status",
                            data.doorHp(), data.doorMaxHp(), data.doorLevel());
            g.drawString(this.font, status, barX, barY + 11, data.doorBroken() ? RED : MUTED);
        }

        // ── 选项行 ───────────────────────────────────────────
        for (int i = 0; i < data.options().size(); i++) {
            drawRow(g, i, mouseX, mouseY);
        }

        // ── 底部提示 ─────────────────────────────────────────
        Component hint = hasAction(SixtySecondsDoorMenu.ACTION_DEPOSIT)
                ? Component.translatable("gui.noellesroles.sixty_seconds.door_hint_deposit")
                : Component.translatable("gui.noellesroles.sixty_seconds.door_hint");
        g.drawCenteredString(this.font, hint, this.width / 2, panelY + panelH - 16, MUTED);
    }

    private void drawRow(GuiGraphics g, int index, int mouseX, int mouseY) {
        OpenShelterDoorS2CPacket.Option option = data.options().get(index);
        int x = panelX + PAD;
        int w = panelW - PAD * 2;
        int y = rowsTop + index * (ROW_H + ROW_GAP);
        boolean enabled = isEnabled(option);
        boolean hover = enabled && isInRect(mouseX, mouseY, x, y, w, ROW_H);

        // hover 平滑过渡（插值而非瞬变）
        float target = hover ? 1F : 0F;
        hoverAnim[index] += (target - hoverAnim[index]) * 0.22F;
        float t = hoverAnim[index];

        int base = enabled ? 0x66231A10 : 0x44140E08;
        g.fill(x, y, x + w, y + ROW_H, blendColors(base, 0x33FFFFFF, t * 0.5F));
        g.renderOutline(x, y, w, ROW_H, enabled ? blendColors(EDGE_IDLE, GOLD, t) : 0xFF3A2E20);
        // 左侧动作色条
        g.fill(x + 1, y + 1, x + 4, y + ROW_H - 1, enabled ? accentColor(option.action()) : 0xFF4A4038);

        Component name = Component.translatable(titleKey(option.action()));
        Component desc = descFor(option, enabled);
        int nameColor = enabled ? blendColors(TEXT, GOLD, t) : 0xFF7A6F5C;
        g.drawString(this.font, name.copy().withStyle(ChatFormatting.BOLD), x + 10 + (int) (t * 3), y + 6,
                nameColor);
        g.drawString(this.font, desc, x + 10, y + 18, enabled ? MUTED : 0xFF6B5F4C);
    }

    /** 「返回住所」冷却本地倒数结束后就地解锁；其余以服务端下发的 enabled 为准。 */
    private boolean isEnabled(OpenShelterDoorS2CPacket.Option option) {
        if (option.enabled()) {
            return true;
        }
        return option.action() == SixtySecondsDoorMenu.ACTION_RETURN && returnRemainingSeconds() <= 0;
    }

    private int returnRemainingSeconds() {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        return (int) Math.ceil(Math.max(0, returnReadyGameTime - now) / 20.0D);
    }

    private Component descFor(OpenShelterDoorS2CPacket.Option option, boolean enabled) {
        return switch (option.action()) {
            case SixtySecondsDoorMenu.ACTION_DEPOSIT -> Component.translatable(
                    "gui.noellesroles.sixty_seconds.door_action.deposit_desc",
                    option.param(), data.storedSupplies());
            case SixtySecondsDoorMenu.ACTION_EXPLORE -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.explore_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.explore_none");
            case SixtySecondsDoorMenu.ACTION_RETURN -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.return_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.return_cd",
                            returnRemainingSeconds());
            case SixtySecondsDoorMenu.ACTION_EVENT ->
                    Component.translatable("gui.noellesroles.sixty_seconds.door_action.event_desc");
            case SixtySecondsDoorMenu.ACTION_VISIT -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_none");
            case SixtySecondsDoorMenu.ACTION_VISIT_PROMPT ->
                    Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_prompt_desc");
            case SixtySecondsDoorMenu.ACTION_VISIT_CHAT -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_chat_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_chat_none");
            case SixtySecondsDoorMenu.ACTION_VISIT_LEAVE ->
                    Component.translatable("gui.noellesroles.sixty_seconds.door_action.visit_leave_desc");
            case SixtySecondsDoorMenu.ACTION_BREAK_CROWBAR -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.break_crowbar_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.break_crowbar_none",
                            option.param());
            case SixtySecondsDoorMenu.ACTION_BREAK_LOCKPICK -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.break_lockpick_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.break_lockpick_none",
                            option.param());
            case SixtySecondsDoorMenu.ACTION_DOOR_INSPECT ->
                    Component.translatable("gui.noellesroles.sixty_seconds.door_action.inspect_desc");
            case SixtySecondsDoorMenu.ACTION_RV_DRIVE -> enabled
                    ? Component.translatable("gui.noellesroles.sixty_seconds.door_action.rv_drive_desc")
                    : Component.translatable("gui.noellesroles.sixty_seconds.door_action.rv_drive_none");
            case SixtySecondsDoorMenu.ACTION_RV_MANAGE ->
                    Component.translatable("gui.noellesroles.sixty_seconds.door_action.rv_manage_desc");
            default -> Component.empty();
        };
    }

    private static String titleKey(int action) {
        return switch (action) {
            case SixtySecondsDoorMenu.ACTION_DEPOSIT -> "gui.noellesroles.sixty_seconds.door_action.deposit";
            case SixtySecondsDoorMenu.ACTION_EXPLORE -> "gui.noellesroles.sixty_seconds.door_action.explore";
            case SixtySecondsDoorMenu.ACTION_RETURN -> "gui.noellesroles.sixty_seconds.door_action.return";
            case SixtySecondsDoorMenu.ACTION_EVENT -> "gui.noellesroles.sixty_seconds.door_action.event";
            case SixtySecondsDoorMenu.ACTION_VISIT -> "gui.noellesroles.sixty_seconds.door_action.visit";
            case SixtySecondsDoorMenu.ACTION_VISIT_PROMPT -> "gui.noellesroles.sixty_seconds.door_action.visit_prompt";
            case SixtySecondsDoorMenu.ACTION_VISIT_CHAT -> "gui.noellesroles.sixty_seconds.door_action.visit_chat";
            case SixtySecondsDoorMenu.ACTION_VISIT_LEAVE -> "gui.noellesroles.sixty_seconds.door_action.visit_leave";
            case SixtySecondsDoorMenu.ACTION_BREAK_CROWBAR -> "gui.noellesroles.sixty_seconds.door_action.break_crowbar";
            case SixtySecondsDoorMenu.ACTION_BREAK_LOCKPICK -> "gui.noellesroles.sixty_seconds.door_action.break_lockpick";
            case SixtySecondsDoorMenu.ACTION_DOOR_INSPECT -> "gui.noellesroles.sixty_seconds.door_action.inspect";
            case SixtySecondsDoorMenu.ACTION_RV_DRIVE -> "gui.noellesroles.sixty_seconds.door_action.rv_drive";
            case SixtySecondsDoorMenu.ACTION_RV_MANAGE -> "gui.noellesroles.sixty_seconds.door_action.rv_manage";
            default -> "gui.noellesroles.sixty_seconds.door_title";
        };
    }

    private static int accentColor(int action) {
        return switch (action) {
            case SixtySecondsDoorMenu.ACTION_DEPOSIT -> GOLD;
            case SixtySecondsDoorMenu.ACTION_EXPLORE -> BLUE;
            case SixtySecondsDoorMenu.ACTION_RETURN -> GREEN;
            case SixtySecondsDoorMenu.ACTION_EVENT -> ORANGE;
            case SixtySecondsDoorMenu.ACTION_VISIT -> PURPLE;
            case SixtySecondsDoorMenu.ACTION_VISIT_PROMPT -> GOLD;
            case SixtySecondsDoorMenu.ACTION_VISIT_CHAT -> BLUE;
            case SixtySecondsDoorMenu.ACTION_VISIT_LEAVE -> GREEN;
            case SixtySecondsDoorMenu.ACTION_BREAK_CROWBAR -> RED;
            case SixtySecondsDoorMenu.ACTION_BREAK_LOCKPICK -> PURPLE;
            case SixtySecondsDoorMenu.ACTION_DOOR_INSPECT -> BLUE;
            case SixtySecondsDoorMenu.ACTION_RV_DRIVE -> ORANGE;
            case SixtySecondsDoorMenu.ACTION_RV_MANAGE -> GOLD;
            default -> MUTED;
        };
    }

    private boolean hasAction(int action) {
        for (OpenShelterDoorS2CPacket.Option option : data.options()) {
            if (option.action() == action) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = panelX + PAD;
            int w = panelW - PAD * 2;
            for (int i = 0; i < data.options().size(); i++) {
                OpenShelterDoorS2CPacket.Option option = data.options().get(i);
                int y = rowsTop + i * (ROW_H + ROW_GAP);
                if (isEnabled(option) && isInRect((int) mouseX, (int) mouseY, x, y, w, ROW_H)) {
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    ClientPlayNetworking.send(new ShelterDoorActionC2SPacket(data.pos(), option.action(),
                            data.rvEntityId()));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isInRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
