package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity;
import net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvPart;
import net.exmo.sre.sixtyseconds.network.RvConsoleActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 房车控制台（潜行右键房车打开）：燃料 / 耐久实时条、升级、配件安装与卸下。
 * 数据全部实时读取已同步的房车实体；操作通过 {@link RvConsoleActionC2SPacket} 发往服务端，
 * 服务端校验后修改实体、同步回客户端，界面下一帧即刷新。风格遵循 docs/ui_style.md。
 */
public class SixtySecondsRvScreen extends Screen {
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;
    private static final int TRACK = 0xFF2A1B0E;

    private final SixtySecondsRvEntity rv;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    /** 本帧渲染出的可点击行（安装/卸下热区），供 mouseClicked 命中判定。 */
    private final List<Row> rows = new ArrayList<>();
    /** 本帧渲染出的座位热区（选座上车），供 mouseClicked 命中判定。 */
    private final List<SeatRow> seatRows = new ArrayList<>();

    private record Row(int x0, int y0, int x1, int y1, SixtySecondsRvPart part, int action) {
        boolean hit(double mx, double my) {
            return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
        }
    }

    private record SeatRow(int x0, int y0, int x1, int y1, int seatIndex) {
        boolean hit(double mx, double my) {
            return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
        }
    }

    public SixtySecondsRvScreen(SixtySecondsRvEntity rv) {
        super(Component.translatable("message.noellesroles.sixty_seconds.rv_console_title"));
        this.rv = rv;
    }

    @Override
    protected void init() {
        panelW = (int) Mth.clamp(this.width * 0.7F, 340, 460);
        panelH = (int) Mth.clamp(this.height * 0.8F, 240, 360);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
        // 升级按钮
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.rv_upgrade"),
                b -> send(RvConsoleActionC2SPacket.ACTION_UPGRADE, 0))
                .bounds(panelX + panelW - 90, panelY + 44, 76, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 40, panelY + panelH - 26, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (rv.isRemoved()) {
            onClose();
            return;
        }
        rows.clear();
        seatRows.clear();
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, DECO);

        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, panelY + 10, GOLD);

        int x = panelX + 14;
        int barW = panelW - 28;
        int y = panelY + 24;

        // 燃料 / 耐久
        int fuelSec = rv.fuelTicks() / 20;
        bar(g, x, y, barW, Component.translatable("message.noellesroles.sixty_seconds.rv_fuel"),
                rv.fuelTicks(), rv.maxFuelTicks(), 0xFFE0A030, fuelSec + "s");
        y += 14;
        bar(g, x, y, barW, Component.translatable("message.noellesroles.sixty_seconds.rv_durability"),
                rv.vehicleHealth(), rv.maxVehicleHealth(),
                rv.isDisabled() ? RED : 0xFFE64848,
                rv.vehicleHealth() + "/" + rv.maxVehicleHealth());
        y += 16;

        // 等级 + 槽位（升级按钮已在 init 里放在右侧）
        g.drawString(this.font, Component.literal("Lv." + rv.upgradeLevel() + "/"
                + SixtySecondsRvEntity.MAX_UPGRADE_LEVEL), x, y + 4, GOLD);
        int used = rv.installedPartCount();
        int slots = rv.equipmentSlotCount();
        g.drawString(this.font, Component.literal("[" + used + "/" + slots + "]"), x + 60, y + 4,
                used >= slots ? RED : TEXT);
        y += 24;

        // 座位选择（2 前 + 2 顶）：点击上车到指定座位
        g.drawString(this.font, Component.literal("座位 / Seats").withStyle(ChatFormatting.BOLD), x, y, GOLD);
        y += 12;
        int seatW = (barW - 12) / 4;
        String[] seatNames = {"前左", "前右", "顶左", "顶右"};
        for (int i = 0; i < SixtySecondsRvEntity.RV_SEAT_COUNT; i++) {
            int sx = x + i * (seatW + 4);
            int occupantId = rv.seatOccupant(i);
            boolean occupied = occupantId != -1;
            boolean isMe = Minecraft.getInstance().player != null
                    && occupantId == Minecraft.getInstance().player.getId();
            int col = isMe ? GOLD : (occupied ? MUTED : GREEN);
            g.fill(sx, y, sx + seatW, y + 14, TRACK);
            g.renderOutline(sx, y, seatW, 14, col);
            String label = seatNames[i] + (isMe ? "(你)" : (occupied ? " 占" : " 空"));
            String trimmed = this.font.plainSubstrByWidth(label, seatW - 4);
            g.drawCenteredString(this.font, trimmed, sx + seatW / 2, y + 3, col);
            seatRows.add(new SeatRow(sx, y, sx + seatW, y + 14, i));
        }
        y += 22;

        int colW = (panelW - 34) / 2;
        int leftX = x;
        int rightX = x + colW + 6;
        // 左列：已装配（点击卸下）
        g.drawString(this.font, Component.literal("已装配 / Installed").withStyle(ChatFormatting.BOLD),
                leftX, y, GOLD);
        // 右列：可安装（背包中的配件，点击安装）
        g.drawString(this.font, Component.literal("可安装 / Available").withStyle(ChatFormatting.BOLD),
                rightX, y, GOLD);
        int rowY = y + 14;

        EnumSet<SixtySecondsRvPart> installed = rv.installedParts();
        int li = 0;
        for (SixtySecondsRvPart part : installed) {
            drawPartRow(g, leftX, rowY + li * 13, colW, part, RvConsoleActionC2SPacket.ACTION_REMOVE, mouseX, mouseY);
            li++;
        }
        int ri = 0;
        for (SixtySecondsRvPart part : availableParts(installed)) {
            drawPartRow(g, rightX, rowY + ri * 13, colW, part, RvConsoleActionC2SPacket.ACTION_INSTALL,
                    mouseX, mouseY);
            ri++;
        }
    }

    private void drawPartRow(GuiGraphics g, int x, int y, int w, SixtySecondsRvPart part, int action,
            int mouseX, int mouseY) {
        int btnW = 16;
        int btnX = x + w - btnW;
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 11;
        Component name = Component.translatable("item.noellesroles.sixty_seconds_rv_" + part.id());
        // 名称按列宽截断
        String trimmed = this.font.plainSubstrByWidth(name.getString(), w - btnW - 4);
        g.drawString(this.font, trimmed, x, y + 1, hover ? TEXT : MUTED);
        // 操作按钮框
        boolean install = action == RvConsoleActionC2SPacket.ACTION_INSTALL;
        int col = install ? GREEN : RED;
        g.fill(btnX, y, btnX + btnW, y + 11, TRACK);
        g.renderOutline(btnX, y, btnW, 11, col);
        g.drawCenteredString(this.font, install ? "+" : "×", btnX + btnW / 2, y + 1, col);
        rows.add(new Row(btnX, y, btnX + btnW, y + 11, part, action));
    }

    /** 背包中持有、且尚未装配的配件（去重）。 */
    private List<SixtySecondsRvPart> availableParts(EnumSet<SixtySecondsRvPart> installed) {
        EnumSet<SixtySecondsRvPart> seen = EnumSet.noneOf(SixtySecondsRvPart.class);
        List<SixtySecondsRvPart> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return out;
        }
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.getItem() instanceof net.exmo.sre.sixtyseconds.content.item.SixtySecondsRvPartItem partItem) {
                SixtySecondsRvPart part = partItem.part();
                if (!installed.contains(part) && seen.add(part)) {
                    out.add(part);
                }
            }
        }
        return out;
    }

    private void bar(GuiGraphics g, int x, int y, int w, Component label, int value, int max, int color,
            String valueText) {
        int barH = 9;
        double ratio = max <= 0 ? 0 : Mth.clamp(value / (double) max, 0, 1);
        g.drawString(this.font, label, x, y, MUTED);
        int barX = x + 46;
        int barRight = x + w;
        g.fill(barX, y, barRight, y + barH, TRACK);
        int fill = (int) Math.round((barRight - barX) * ratio);
        if (fill > 0) {
            g.fill(barX, y, barX + fill, y + barH, color);
        }
        g.drawString(this.font, valueText, barRight - this.font.width(valueText) - 3, y, TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 座位选择优先
            for (SeatRow sr : seatRows) {
                if (sr.hit(mouseX, mouseY)) {
                    send(RvConsoleActionC2SPacket.ACTION_BOARD, sr.seatIndex());
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            for (Row row : rows) {
                if (row.hit(mouseX, mouseY)) {
                    send(row.action(), row.part().ordinal());
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void send(int action, int partOrdinal) {
        ClientPlayNetworking.send(new RvConsoleActionC2SPacket(rv.getId(), action, partOrdinal));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
