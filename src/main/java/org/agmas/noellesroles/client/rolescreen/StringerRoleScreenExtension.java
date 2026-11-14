package org.agmas.noellesroles.client.rolescreen;

import java.awt.Point;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.packet.StringerLinkC2SPayload;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/** 双阶段选人，搜索、排序、分页由 RoleScreenHelper 提供；客户端不施加牵引。 */
public final class StringerRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    private UUID first;
    private Component firstName = Component.empty();

    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> result = new RoleScreenHelper<>(player, ModRoles.STRINGER,
                this::widget, TEXT_PROVIDER, this::drawHint, this::players);
        result.setNameExtractor(info -> info.getProfile().getName());
        return result;
    }

    private Button widget(LimitedInventoryScreen screen, int x, int y, PlayerInfo info, int index) {
        Component name = Component.nullToEmpty(info.getProfile().getName());
        Button button = new Button(x, y, 16, 16, name, ignored -> {
            UUID id = info.getProfile().getId();
            if (first == null) {
                first = id;
                firstName = name;
            } else if (first.equals(id)) {
                first = null;
                firstName = Component.empty();
            } else {
                ClientPlayNetworking.send(new StringerLinkC2SPayload(first, id));
                first = null;
                screen.onClose();
            }
        }, supplier -> supplier.get()) {
            @Override
            protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                super.renderWidget(graphics, mouseX, mouseY, delta);
                PlayerFaceRenderer.draw(graphics, info.getSkin().texture(), getX(), getY(), 16);
                if (info.getProfile().getId().equals(first)) {
                    graphics.renderOutline(getX() - 1, getY() - 1, 18, 18, 0xFFFFD700);
                }
            }
        };
        button.setTooltip(Tooltip.create(name));
        screen.addRoleWidget(button);
        return button;
    }

    private void drawHint(GuiGraphics graphics, Point point) {
        Minecraft client = Minecraft.getInstance();
        Component hint = first == null ? Component.translatable("hud.noellesroles.stringer.select_first")
                : Component.translatable("hud.noellesroles.stringer.select_second", firstName);
        graphics.drawString(client.font, hint, point.x - client.font.width(hint) / 2, point.y + 40, 0xFFFFD700);
    }

    private List<PlayerInfo> players() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) return List.of();
        return client.getConnection().getListedOnlinePlayers().stream()
                .filter(info -> info.getGameMode() == GameType.ADVENTURE || info.getGameMode() == GameType.SURVIVAL)
                .toList();
    }
}
