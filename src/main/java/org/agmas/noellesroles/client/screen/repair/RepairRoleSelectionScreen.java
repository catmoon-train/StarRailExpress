package org.agmas.noellesroles.client.screen.repair;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairRoleDefinition;
import org.agmas.noellesroles.packet.RepairRoleSelectC2SPacket;

import java.util.List;

public class RepairRoleSelectionScreen extends Screen {
    private static final int CARD_W = 116;
    private static final int CARD_H = 54;

    private final RepairRoleDefinition.Faction faction;
    private final long endTick;
    private final List<String> playerNames;
    private RepairRoleDefinition previewRole;
    private CameraType previousCameraType = CameraType.FIRST_PERSON;
    private boolean allowClose;

    public RepairRoleSelectionScreen(String faction, long endTick, List<String> playerNames) {
        super(Component.translatable("screen.noellesroles.repair_roles.title"));
        this.faction = RepairRoleDefinition.Faction.valueOf(faction.toUpperCase());
        this.endTick = endTick;
        this.playerNames = playerNames;
        List<RepairRoleDefinition> roles = RepairRoleDefinition.byFaction(this.faction);
        this.previewRole = roles.isEmpty() ? null : roles.getFirst();
    }

    @Override
    protected void init() {
        if (minecraft != null) {
            previousCameraType = minecraft.options.getCameraType();
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        rebuildCards();
    }

    private void rebuildCards() {
        clearWidgets();
        List<RepairRoleDefinition> roles = RepairRoleDefinition.byFaction(faction);
        int gap = 8;
        int total = roles.size() * CARD_W + Math.max(0, roles.size() - 1) * gap;
        int startX = Math.max(12, (width - total) / 2);
        int y = height - CARD_H - 18;
        for (int i = 0; i < roles.size(); i++) {
            RepairRoleDefinition role = roles.get(i);
            int x = startX + i * (CARD_W + gap);
            addRenderableWidget(Button.builder(Component.literal(""), button -> {
                previewRole = role;
                if (owns(role)) {
                    ClientPlayNetworking.send(new RepairRoleSelectC2SPacket(role.id));
                } else if (minecraft != null && minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.translatable("screen.noellesroles.repair_roles.locked_shop")
                                    .withStyle(ChatFormatting.YELLOW),
                            true);
                }
            }).bounds(x, y, CARD_W, CARD_H).build());
        }
    }

    @Override
    public void tick() {
        if (remainingSeconds() <= 0) {
            allowClose = true;
            closeNaturally();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        drawTopStrip(graphics);
        drawPlayerList(graphics);
        super.render(graphics, mouseX, mouseY, delta);
        drawRoleCards(graphics, mouseX, mouseY);
        drawPreview(graphics);
    }

    private void drawTopStrip(GuiGraphics graphics) {
        int panelW = 238;
        int panelH = 36;
        int x = (width - panelW) / 2;
        int y = 12;
        graphics.fill(x, y, x + panelW, y + panelH, 0xAA120A05);
        graphics.renderOutline(x, y, panelW, panelH, 0xD6D3A047);
        graphics.drawCenteredString(font, title, width / 2, y + 6, 0xFFFFE3A2);
        graphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.repair_roles.timer", remainingSeconds()),
                width / 2, y + 20, 0xFFFFC44D);
    }

    private void drawPlayerList(GuiGraphics graphics) {
        int x = 14;
        int y = 62;
        int panelW = 132;
        int panelH = Math.min(96, 24 + Math.min(7, playerNames.size()) * 10);
        graphics.fill(x, y, x + panelW, y + panelH, 0x88120905);
        graphics.renderOutline(x, y, panelW, panelH, 0x99AA7A28);
        graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.team", faction.displayName()),
                x + 8, y + 7, 0xFFFFE3A2);
        for (int i = 0; i < Math.min(7, playerNames.size()); i++) {
            graphics.drawString(font, Component.literal(playerNames.get(i)), x + 8, y + 21 + i * 10, 0xFFE2D3BC);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    private void drawRoleCards(GuiGraphics graphics, int mouseX, int mouseY) {
        List<RepairRoleDefinition> roles = RepairRoleDefinition.byFaction(faction);
        int gap = 8;
        int total = roles.size() * CARD_W + Math.max(0, roles.size() - 1) * gap;
        int startX = Math.max(12, (width - total) / 2);
        int y = height - CARD_H - 18;
        for (int i = 0; i < roles.size(); i++) {
            RepairRoleDefinition role = roles.get(i);
            int x = startX + i * (CARD_W + gap);
            boolean owned = owns(role);
            boolean selected = isSelected(role);
            boolean hovered = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
            int fill = selected ? 0xEE3B2C16 : hovered ? 0xEE2A2114 : 0xDC1A1009;
            graphics.fill(x, y, x + CARD_W, y + CARD_H, fill);
            graphics.renderOutline(x, y, CARD_W, CARD_H, selected ? 0xFFFFD76A : 0xCC9C6B24);
            graphics.drawString(font, role.displayName(), x + 8, y + 7, owned ? 0xFFFFEBD0 : 0xFF9E8F7A);
            Component state = owned ? Component.translatable("screen.noellesroles.repair_roles.owned")
                    : Component.translatable("screen.noellesroles.repair_roles.locked");
            graphics.drawString(font, state, x + 8, y + 21, owned ? 0xFF87E69B : 0xFFFF8270);
            graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.active_skill_short"),
                    x + 8, y + 35, 0xFFBFA874);
            graphics.drawString(font, activeSkillName(role), x + 49, y + 35, 0xFFE4D6B7);
        }
    }

    private void drawPreview(GuiGraphics graphics) {
        if (previewRole == null) {
            return;
        }
        int panelW = 170;
        int panelH = 112;
        int x = width - panelW - 14;
        int y = Math.max(58, height - CARD_H - panelH - 30);
        graphics.fill(x, y, x + panelW, y + panelH, 0xAA120905);
        graphics.renderOutline(x, y, panelW, panelH, 0xCCD3A047);
        graphics.drawString(font, previewRole.displayName().copy().withStyle(ChatFormatting.GOLD), x + 9, y + 9, 0xFFFFE3A2);
        graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.active_skill"),
                x + 9, y + 26, 0xFFFFC44D);
        graphics.drawString(font, activeSkillName(previewRole), x + 66, y + 26, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.passive"),
                x + 9, y + 40, 0xFFFFC44D);
        graphics.drawWordWrap(font, previewRole.description(), x + 9, y + 54, panelW - 18, 0xFFE4D6B7);
        if (!owns(previewRole)) {
            graphics.drawString(font, Component.translatable("screen.noellesroles.repair_roles.locked_shop"),
                    x + 9, y + panelH - 17, 0xFFFF8270);
        }
    }

    private Component activeSkillName(RepairRoleDefinition role) {
        return Component.translatable("hud.noellesroles.repair.active_skill." + role.id);
    }

    private long remainingSeconds() {
        Minecraft client = Minecraft.getInstance();
        long now = client.level != null ? client.level.getGameTime() : 0L;
        return Math.max(0L, (endTick - now + 19L) / 20L);
    }

    private boolean owns(RepairRoleDefinition role) {
        if (minecraft == null || minecraft.player == null) {
            return role.starter;
        }
        return ModComponents.REPAIR_ROLES.get(minecraft.player).owns(role);
    }

    private boolean isSelected(RepairRoleDefinition role) {
        if (minecraft == null || minecraft.player == null) {
            return role.starter;
        }
        return ModComponents.REPAIR_ROLES.get(minecraft.player).selectedRole(faction) == role;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return allowClose ||minecraft.player.hasPermissions(2);
    }

    @Override
    public void onClose() {
        if (allowClose||minecraft.player.hasPermissions(2)) {
            closeNaturally();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeNaturally() {
        if (minecraft == null) {
            return;
        }
        minecraft.options.setCameraType(previousCameraType);
        if (minecraft.screen == this) {
            minecraft.setScreen(null);
        }
    }
}
