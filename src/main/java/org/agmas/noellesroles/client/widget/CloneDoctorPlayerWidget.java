package org.agmas.noellesroles.client.widget;

import java.util.List;

import org.agmas.noellesroles.client.CloneDoctorClient;
import org.agmas.noellesroles.packet.CloneDoctorActionC2SPacket;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** 只向服务端提交目标 UUID，不要求目标实体加载，也不在本地变更职业。 */
public final class CloneDoctorPlayerWidget extends Button {
    private final PlayerInfo target;

    public CloneDoctorPlayerWidget(LimitedInventoryScreen screen, int x, int y, PlayerInfo target) {
        super(x, y, 16, 16, Component.literal(target.getProfile().getName()), button -> {
            if (CloneDoctorClient.canSelectTarget(target.getProfile().getId())) {
                ClientPlayNetworking.send(new CloneDoctorActionC2SPacket(target.getProfile().getId(), false));
                screen.onClose();
            }
        }, DEFAULT_NARRATION);
        this.target = target;
        this.active = CloneDoctorClient.canSelectTarget(target.getProfile().getId());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.renderWidget(graphics, mouseX, mouseY, delta);
        active = CloneDoctorClient.canSelectTarget(target.getProfile().getId());
        graphics.fill(getX() - 2, getY() - 2, getX() + 18, getY() + 18, 0xD81A1008);
        graphics.renderOutline(getX() - 2, getY() - 2, 20, 20, isHovered() ? 0xFFD4AF37 : 0xFF8B6914);
        PlayerFaceRenderer.draw(graphics, target.getSkin().texture(), getX(), getY(), 16);
        if (!active) {
            graphics.fill(getX(), getY(), getX() + 16, getY() + 16, 0x99000000);
        }
        if (isHoveredOrFocused()) {
            int ticks = CloneDoctorClient.state().copyCooldown();
            graphics.renderComponentTooltip(Minecraft.getInstance().font, List.of(
                    getMessage(),
                    Component.translatable("hud.noellesroles.clone_doctor.select"),
                    Component.translatable("hud.noellesroles.clone_doctor.cost", CloneDoctorClient.COPY_COST),
                    Component.translatable("hud.noellesroles.clone_doctor.copy_cooldown",
                            ticks / 20 + (ticks % 20 > 0 ? 1 : 0))), mouseX, mouseY);
        }
    }

    @Override
    public void renderString(GuiGraphics graphics, Font font, int color) {
        // 头像代替按钮文字，姓名由悬浮提示和原版无障碍旁白提供。
    }
}
