package org.agmas.noellesroles.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.client.Error404Client;
import org.agmas.noellesroles.packet.Error404ActionC2SPacket;
import org.agmas.noellesroles.packet.Error404StateS2CPacket;
import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Error404 俯视战术地图。坐标投影在网格上，不是玩家名单。
 *
 * <p>选中自己的傀儡后，右键地图下达移动；左键普通玩家下达追击。
 * 引爆需要先点一次确认。状态刷新只重读缓存，不重建本界面。
 */
public class Error404CommandScreen extends Screen {
    private static final int BG_TOP = 0xF0100818;
    private static final int BG_BOTTOM = 0xF0060410;
    private static final int PANEL = 0xE0140C1C;
    private static final int PANEL_EDGE = 0xFF6A3A8A;
    private static final int GRID = 0x336E4A92;
    private static final int GRID_MAJOR = 0x556E4A92;
    private static final int TEXT = 0xFFF4E8FF;
    private static final int MUTED = 0xFF9A86AE;
    private static final int GOLD = 0xFFD4AF37;
    private static final int PUPPET = 0xFFC45CFF;
    private static final int ASSIMILATING = 0xFFE07AD4;
    private static final int PLAYER = 0xFF8EE7FF;
    private static final int DANGER = 0xFFE06B65;
    private static final int OK = 0xFF72C17B;
    private static final double MIN_SCALE = 0.02D;
    private static final double MAX_SCALE = 18.0D;

    private final List<Error404StateS2CPacket.Contact> view = new ArrayList<>();
    private UUID selectedPuppet;
    private UUID pendingDetonate;
    private String statusKey = "screen.noellesroles.error404.status.idle";
    private Object[] statusArgs = new Object[0];
    private int statusColor = MUTED;
    private boolean fitted;
    private boolean dragging;
    private double dragOriginX;
    private double dragOriginY;
    private double panOriginX;
    private double panOriginY;
    private double centerX;
    private double centerZ;
    private double scale = 4.0D;

    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;
    private int sideX;
    private int sideW;

    public Error404CommandScreen() {
        super(Component.translatable("screen.noellesroles.error404.title"));
    }

    /** 服务端刷新时由客户端入口调用。选择、平移和缩放都留在当前实例里。 */
    public void onStateRefreshed() {
        reconcileSelection();
    }

    /** 服务端裁决覆盖本地的意图提示，失败时不再显示已经成功执行。 */
    public void onFeedback(String suffix) {
        setStatus("message.noellesroles.error404." + suffix, GOLD);
    }

    @Override
    protected void init() {
        super.init();
        int margin = 10;
        int gap = 8;
        sideW = Mth.clamp((int) (this.width * 0.28F), 148, 230);
        mapX = margin;
        mapY = 28;
        mapW = Math.max(80, this.width - margin * 2 - gap - sideW);
        mapH = Math.max(60, this.height - mapY - 28);
        sideX = mapX + mapW + gap;
        if (!fitted) {
            fitAll();
            fitted = true;
        }
        reconcileSelection();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refreshView();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawMap(graphics, mouseX, mouseY);
        drawSide(graphics, mouseX, mouseY);
        graphics.drawString(this.font, this.title, mapX, 10, TEXT, false);
        graphics.drawString(this.font, Component.translatable(statusKey, statusArgs), mapX, this.height - 16,
                statusColor, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, BG_TOP, BG_BOTTOM);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && hit(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
            Error404StateS2CPacket.Contact hit = contactAt(mouseX, mouseY);
            if (hit != null && hit.state() == 0) {
                attack(hit);
            } else if (hit != null) {
                select(hit);
            } else {
                dragging = true;
                dragOriginX = mouseX;
                dragOriginY = mouseY;
                panOriginX = centerX;
                panOriginY = centerZ;
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
            moveTo(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return clickSide(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            centerX = panOriginX - (mouseX - dragOriginX) / scale;
            centerZ = panOriginY - (mouseY - dragOriginY) / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hit(mouseX, mouseY, mapX, mapY, mapW, mapH) || scrollY == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double worldX = toWorldX(mouseX);
        double worldZ = toWorldZ(mouseY);
        scale = Mth.clamp(scale * (scrollY > 0.0D ? 1.15D : 1.0D / 1.15D), MIN_SCALE, MAX_SCALE);
        centerX = worldX - (mouseX - mapX - mapW / 2.0D) / scale;
        centerZ = worldZ - (mouseY - mapY - mapH / 2.0D) / scale;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F) {
            fitAll();
            setStatus("screen.noellesroles.error404.status.fitted", MUTED);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void refreshView() {
        view.clear();
        view.addAll(Error404Client.contacts());
    }

    private void reconcileSelection() {
        refreshView();
        if (selectedPuppet != null && find(selectedPuppet) == null) {
            selectedPuppet = null;
            pendingDetonate = null;
            setStatus("screen.noellesroles.error404.status.lost", MUTED);
        }
        if (pendingDetonate != null && !pendingDetonate.equals(selectedPuppet)) {
            pendingDetonate = null;
        }
    }

    private boolean clickSide(double mouseX, double mouseY) {
        int y = mapY + 22;
        int shown = 0;
        for (Error404StateS2CPacket.Contact contact : view) {
            if (contact.state() == 0 || shown >= 2) {
                continue;
            }
            if (hit(mouseX, mouseY, sideX + 6, y, sideW - 12, 34)) {
                select(contact);
                return true;
            }
            y += 38;
            shown++;
        }
        int buttonY = mapY + mapH - 86;
        if (hit(mouseX, mouseY, sideX + 6, buttonY, sideW - 12, 16)) {
            hold();
            return true;
        }
        if (hit(mouseX, mouseY, sideX + 6, buttonY + 18, sideW - 12, 16)) {
            detonate();
            return true;
        }
        if (hit(mouseX, mouseY, sideX + 6, buttonY + 36, sideW - 12, 16)) {
            accelerate();
            return true;
        }
        if (hit(mouseX, mouseY, sideX + 6, buttonY + 54, sideW - 12, 16)) {
            send(Error404ActionC2SPacket.EXIT, Error404ActionC2SPacket.NONE, Error404ActionC2SPacket.NONE, 0.0D, 0.0D,
                    0.0D);
            setStatus("screen.noellesroles.error404.status.exit", GOLD);
            return true;
        }
        if (hit(mouseX, mouseY, sideX + 6, buttonY + 72, sideW - 12, 14)) {
            fitAll();
            setStatus("screen.noellesroles.error404.status.fitted", MUTED);
            return true;
        }
        return false;
    }

    private void select(Error404StateS2CPacket.Contact contact) {
        if (contact.state() != 1 && contact.state() != 2) {
            setStatus("screen.noellesroles.error404.status.not_puppet", DANGER, contact.name());
            return;
        }
        selectedPuppet = contact.id();
        if (pendingDetonate != null && !pendingDetonate.equals(selectedPuppet)) {
            pendingDetonate = null;
        }
        send(Error404ActionC2SPacket.SELECT, contact.id(), Error404ActionC2SPacket.NONE, contact.x(), contact.y(),
                contact.z());
        setStatus("screen.noellesroles.error404.status.selected", PUPPET, contact.name());
    }

    private void moveTo(double mouseX, double mouseY) {
        Error404StateS2CPacket.Contact puppet = selected();
        if (puppet == null || puppet.state() != 1) {
            setStatus(puppet == null ? "screen.noellesroles.error404.status.need_puppet"
                    : "screen.noellesroles.error404.status.move_ready_only", DANGER);
            return;
        }
        double x = toWorldX(mouseX);
        double z = toWorldZ(mouseY);
        send(Error404ActionC2SPacket.MOVE, puppet.id(), Error404ActionC2SPacket.NONE, x, puppet.y(), z);
        setStatus("screen.noellesroles.error404.status.move", OK, format(x), format(z));
    }

    private void attack(Error404StateS2CPacket.Contact target) {
        Error404StateS2CPacket.Contact puppet = selected();
        if (puppet == null || puppet.state() != 1) {
            setStatus(puppet == null ? "screen.noellesroles.error404.status.need_puppet"
                    : "screen.noellesroles.error404.status.attack_ready_only", DANGER);
            return;
        }
        if (target.state() != 0) {
            setStatus("screen.noellesroles.error404.status.attack_player_only", DANGER);
            return;
        }
        send(Error404ActionC2SPacket.ATTACK, puppet.id(), target.id(), target.x(), target.y(), target.z());
        setStatus("screen.noellesroles.error404.status.attack", DANGER, target.name());
    }

    private void hold() {
        Error404StateS2CPacket.Contact puppet = selected();
        if (puppet == null || puppet.state() != 1) {
            setStatus(puppet == null ? "screen.noellesroles.error404.status.need_puppet"
                    : "screen.noellesroles.error404.status.hold_ready_only", DANGER);
            return;
        }
        send(Error404ActionC2SPacket.HOLD, puppet.id(), Error404ActionC2SPacket.NONE, puppet.x(), puppet.y(),
                puppet.z());
        setStatus("screen.noellesroles.error404.status.hold", GOLD, puppet.name());
    }

    private void detonate() {
        Error404StateS2CPacket.Contact puppet = selected();
        if (puppet == null || puppet.state() != 1) {
            pendingDetonate = null;
            setStatus(puppet == null ? "screen.noellesroles.error404.status.need_puppet"
                    : "screen.noellesroles.error404.status.detonate_ready_only", DANGER);
            return;
        }
        if (!puppet.id().equals(pendingDetonate)) {
            pendingDetonate = puppet.id();
            setStatus("screen.noellesroles.error404.status.confirm_detonate", DANGER, puppet.name());
            return;
        }
        pendingDetonate = null;
        send(Error404ActionC2SPacket.DETONATE, puppet.id(), Error404ActionC2SPacket.NONE, puppet.x(), puppet.y(),
                puppet.z());
        setStatus("screen.noellesroles.error404.status.detonate", DANGER, puppet.name());
    }

    private void accelerate() {
        Error404StateS2CPacket.Contact puppet = selected();
        if (puppet == null || puppet.state() != 2) {
            setStatus(puppet == null ? "screen.noellesroles.error404.status.need_puppet"
                    : "screen.noellesroles.error404.status.accelerate_only", DANGER);
            return;
        }
        send(Error404ActionC2SPacket.ACCELERATE, puppet.id(), Error404ActionC2SPacket.NONE, puppet.x(), puppet.y(),
                puppet.z());
        setStatus("screen.noellesroles.error404.status.accelerate", ASSIMILATING, puppet.name());
    }

    private void send(int action, UUID puppet, UUID target, double x, double y, double z) {
        if (!ClientPlayNetworking.canSend(Error404ActionC2SPacket.ID)) {
            setStatus("screen.noellesroles.error404.status.offline", DANGER);
            return;
        }
        ClientPlayNetworking.send(new Error404ActionC2SPacket(action, puppet, target, x, y, z));
    }

    private void drawMap(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xE0080612);
        graphics.renderOutline(mapX, mapY, mapW, mapH, PANEL_EDGE);
        graphics.enableScissor(mapX + 1, mapY + 1, mapX + mapW - 1, mapY + mapH - 1);
        drawGrid(graphics);
        for (Error404StateS2CPacket.Contact contact : view) {
            drawContact(graphics, contact, contact.id().equals(selectedPuppet));
        }
        if (hit(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
            int sx = Mth.floor(mouseX);
            int sy = Mth.floor(mouseY);
            graphics.fill(sx, mapY + 1, sx + 1, mapY + mapH - 1, 0x44FFFFFF);
            graphics.fill(mapX + 1, sy, mapX + mapW - 1, sy + 1, 0x44FFFFFF);
            Component cursor = Component.translatable("screen.noellesroles.error404.cursor", format(toWorldX(mouseX)),
                    format(toWorldZ(mouseY)));
            graphics.drawString(this.font, cursor, mapX + 6, mapY + mapH - 14, TEXT, false);
        }
        graphics.disableScissor();
        if (view.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.noellesroles.error404.empty"),
                    mapX + mapW / 2, mapY + mapH / 2 - 4, MUTED);
        }
        graphics.drawString(this.font, Component.translatable("screen.noellesroles.error404.map_hint"), mapX + 6,
                mapY + 6, MUTED, false);
        graphics.drawString(this.font, Component.translatable("screen.noellesroles.error404.map_hint_2"), mapX + 6,
                mapY + 16, MUTED, false);
    }

    private void drawGrid(GuiGraphics graphics) {
        double step = gridStep();
        double left = toWorldX(mapX);
        double right = toWorldX(mapX + mapW);
        double top = toWorldZ(mapY);
        double bottom = toWorldZ(mapY + mapH);
        int firstX = Mth.floor(Math.min(left, right) / step);
        int lastX = Mth.ceil(Math.max(left, right) / step);
        int firstZ = Mth.floor(Math.min(top, bottom) / step);
        int lastZ = Mth.ceil(Math.max(top, bottom) / step);
        for (int i = firstX; i <= lastX; i++) {
            int x = Mth.floor(toScreenX(i * step));
            graphics.fill(x, mapY + 1, x + 1, mapY + mapH - 1, i % 4 == 0 ? GRID_MAJOR : GRID);
        }
        for (int i = firstZ; i <= lastZ; i++) {
            int y = Mth.floor(toScreenZ(i * step));
            graphics.fill(mapX + 1, y, mapX + mapW - 1, y + 1, i % 4 == 0 ? GRID_MAJOR : GRID);
        }
    }

    private void drawContact(GuiGraphics graphics, Error404StateS2CPacket.Contact contact, boolean selected) {
        int x = Mth.floor(toScreenX(contact.x()));
        int y = Mth.floor(toScreenZ(contact.z()));
        int color = switch (contact.state()) {
            case 1 -> PUPPET;
            case 2 -> ASSIMILATING;
            default -> PLAYER;
        };
        int size = selected ? 5 : 3;
        graphics.fill(x - size, y - size, x + size + 1, y + size + 1, 0xCC08040E);
        graphics.fill(x - size + 1, y - size + 1, x + size, y + size, color);
        if (contact.order() == 1) {
            graphics.fill(x + size + 2, y - 1, x + size + 6, y + 1, GOLD);
        } else if (contact.order() == 2) {
            graphics.fill(x - 1, y - size - 6, x + 1, y - size - 2, DANGER);
        }
        String name = trim(contact.name(), 16);
        Component label = Component.translatable("screen.noellesroles.error404.marker", name,
                Mth.floor(contact.y()));
        int textX = x + size + 4;
        int textWidth = this.font.width(label);
        if (textX + textWidth > mapX + mapW - 4) {
            textX = x - size - 4 - textWidth;
        }
        graphics.drawString(this.font, label, textX, y - 4, TEXT, false);
    }

    private void drawSide(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(sideX, mapY, sideX + sideW, mapY + mapH, PANEL);
        graphics.renderOutline(sideX, mapY, sideW, mapH, PANEL_EDGE);
        graphics.fill(sideX + 1, mapY + 1, sideX + sideW - 1, mapY + 2, 0x33FFE8C0);
        graphics.drawString(this.font, Component.translatable("screen.noellesroles.error404.slots"), sideX + 8,
                mapY + 8, GOLD, false);

        int y = mapY + 22;
        int shown = 0;
        for (Error404StateS2CPacket.Contact contact : view) {
            if (contact.state() == 0) {
                continue;
            }
            if (shown >= 2) {
                break;
            }
            boolean selected = contact.id().equals(selectedPuppet);
            boolean hover = hit(mouseX, mouseY, sideX + 6, y, sideW - 12, 34);
            graphics.fill(sideX + 6, y, sideX + sideW - 6, y + 34,
                    selected ? 0x665A2E78 : hover ? 0x332E1840 : 0x22000000);
            graphics.renderOutline(sideX + 6, y, sideW - 12, 34, selected ? PUPPET : PANEL_EDGE);
            graphics.drawString(this.font, trim(contact.name(), 18), sideX + 10, y + 4, TEXT, false);
            graphics.drawString(this.font, Component.translatable(stateKey(contact.state())), sideX + 10, y + 15,
                    contact.state() == 2 ? ASSIMILATING : PUPPET, false);
            graphics.drawString(this.font, Component.translatable("screen.noellesroles.error404.slot_meta",
                    Mth.floor(contact.y()), contact.seconds(), Component.translatable(orderKey(contact.order()))),
                    sideX + 10, y + 24, MUTED, false);
            y += 38;
            shown++;
        }
        if (shown == 0) {
            graphics.drawString(this.font, Component.translatable("screen.noellesroles.error404.no_puppets"),
                    sideX + 8, y + 4, MUTED, false);
        }

        int buttonY = mapY + mapH - 86;
        drawButton(graphics, mouseX, mouseY, buttonY, 16, "screen.noellesroles.error404.button.hold", false);
        boolean confirming = pendingDetonate != null && pendingDetonate.equals(selectedPuppet);
        drawButton(graphics, mouseX, mouseY, buttonY + 18, 16,
                confirming ? "screen.noellesroles.error404.button.confirm_detonate"
                        : "screen.noellesroles.error404.button.detonate",
                confirming);
        drawButton(graphics, mouseX, mouseY, buttonY + 36, 16, "screen.noellesroles.error404.button.accelerate", false);
        drawButton(graphics, mouseX, mouseY, buttonY + 54, 16, "screen.noellesroles.error404.button.exit", false);
        drawButton(graphics, mouseX, mouseY, buttonY + 72, 14, "screen.noellesroles.error404.button.fit", false);
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY, int y, int height, String key, boolean danger) {
        boolean hover = hit(mouseX, mouseY, sideX + 6, y, sideW - 12, height);
        graphics.fill(sideX + 6, y, sideX + sideW - 6, y + height,
                danger ? 0x66E06B65 : hover ? 0x443C2458 : 0x33180E24);
        graphics.renderOutline(sideX + 6, y, sideW - 12, height, danger ? DANGER : hover ? GOLD : PANEL_EDGE);
        graphics.drawCenteredString(this.font, Component.translatable(key), sideX + sideW / 2, y + (height - 8) / 2,
                danger ? 0xFFFFD0CC : TEXT);
    }

    private void fitAll() {
        refreshView();
        if (view.isEmpty()) {
            scale = 4.0D;
            return;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Error404StateS2CPacket.Contact contact : view) {
            minX = Math.min(minX, contact.x());
            maxX = Math.max(maxX, contact.x());
            minZ = Math.min(minZ, contact.z());
            maxZ = Math.max(maxZ, contact.z());
        }
        centerX = (minX + maxX) / 2.0D;
        centerZ = (minZ + maxZ) / 2.0D;
        double span = Math.max(16.0D, Math.max(maxX - minX, maxZ - minZ) + 24.0D);
        scale = Mth.clamp(Math.min(mapW, mapH) / span, MIN_SCALE, MAX_SCALE);
    }

    private Error404StateS2CPacket.Contact selected() {
        return selectedPuppet == null ? null : find(selectedPuppet);
    }

    private Error404StateS2CPacket.Contact find(UUID id) {
        for (Error404StateS2CPacket.Contact contact : view) {
            if (id.equals(contact.id())) {
                return contact;
            }
        }
        return null;
    }

    private Error404StateS2CPacket.Contact contactAt(double mouseX, double mouseY) {
        Error404StateS2CPacket.Contact best = null;
        double bestDistance = 10.0D * 10.0D;
        for (Error404StateS2CPacket.Contact contact : view) {
            double dx = toScreenX(contact.x()) - mouseX;
            double dy = toScreenZ(contact.z()) - mouseY;
            double distance = dx * dx + dy * dy;
            if (distance <= bestDistance) {
                best = contact;
                bestDistance = distance;
            }
        }
        return best;
    }

    private double gridStep() {
        double raw = 16.0D / Math.max(0.01D, scale) * 8.0D;
        double power = Math.pow(10.0D, Math.floor(Math.log10(raw)));
        double normalized = raw / power;
        double step = normalized < 2.0D ? 1.0D : normalized < 5.0D ? 2.0D : 5.0D;
        return Math.max(1.0D, step * power);
    }

    private double toScreenX(double worldX) {
        return mapX + mapW / 2.0D + (worldX - centerX) * scale;
    }

    private double toScreenZ(double worldZ) {
        return mapY + mapH / 2.0D + (worldZ - centerZ) * scale;
    }

    private double toWorldX(double screenX) {
        return centerX + (screenX - mapX - mapW / 2.0D) / scale;
    }

    private double toWorldZ(double screenY) {
        return centerZ + (screenY - mapY - mapH / 2.0D) / scale;
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static String stateKey(int state) {
        return switch (state) {
            case 1 -> "screen.noellesroles.error404.state.puppet";
            case 2 -> "screen.noellesroles.error404.state.assimilating";
            default -> "screen.noellesroles.error404.state.player";
        };
    }

    private static String orderKey(int order) {
        return switch (order) {
            case 1 -> "screen.noellesroles.error404.order.move";
            case 2 -> "screen.noellesroles.error404.order.attack";
            default -> "screen.noellesroles.error404.order.hold";
        };
    }

    private static String trim(String value, int max) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static String format(double value) {
        return Integer.toString(Mth.floor(value));
    }

    private void setStatus(String key, int color, Object... args) {
        statusKey = key;
        statusArgs = args;
        statusColor = color;
    }
}
