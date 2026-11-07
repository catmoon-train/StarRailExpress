package org.agmas.noellesroles.client.rolescreen;

import java.awt.Point;
import java.util.List;

import org.agmas.noellesroles.client.CloneDoctorClient;
import org.agmas.noellesroles.client.widget.CloneDoctorPlayerWidget;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleScreenHelper;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** 基准职业的技能二选人列表：使用玩家信息而非已加载实体，包含自己并支持分页搜索。 */
public final class CloneDoctorRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    @Override
    protected RoleScreenHelper<PlayerInfo> createHelper(LocalPlayer player) {
        RoleScreenHelper<PlayerInfo> helper = new RoleScreenHelper<>(player, ModRoles.CLONE_DOCTOR,
                this::createWidget, TEXT_PROVIDER, this::drawHint, this::getEligiblePlayers);
        helper.setNameExtractor(info -> info.getProfile().getName());
        return helper;
    }

    private CloneDoctorPlayerWidget createWidget(LimitedInventoryScreen screen, int x, int y,
            PlayerInfo target, int index) {
        return screen.addRoleWidget(new CloneDoctorPlayerWidget(screen, x, y, target));
    }

    private List<PlayerInfo> getEligiblePlayers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.getConnection() == null
                || SREClient.gameComponent == null) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(info -> SREClient.gameComponent.isKillerTeam(info.getProfile().getId()))
                .filter(info -> SREPlayerUtils.isPlayerAlive(client.level, info.getProfile().getId()))
                .toList();
    }

    private void drawHint(GuiGraphics graphics, Point center) {
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, Component.translatable("hud.noellesroles.clone_doctor.select"),
                center.x, center.y + 40, 0xFFD4AF37);
        graphics.drawCenteredString(font,
                Component.translatable("hud.noellesroles.clone_doctor.cost", CloneDoctorClient.COPY_COST),
                center.x, center.y + 40 - font.lineHeight - 2, 0xFFFFF4DC);
    }
}
