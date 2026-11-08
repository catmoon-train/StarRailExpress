package org.agmas.noellesroles.client;

import java.util.UUID;

import org.agmas.noellesroles.client.rolescreen.CloneDoctorRoleScreenExtension;
import org.agmas.noellesroles.packet.CloneDoctorActionC2SPacket;
import org.agmas.noellesroles.packet.CloneDoctorStateS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MoneyUtils;

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.event.client.LimitedInventoryScreenEvents;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SREPlayerUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** 本人的临时状态、倒计时与跨复制职业的返回入口，不替换被复制职业的扩展或按键。 */
public final class CloneDoctorClient {
    public static final int COPY_COST = 100;
    private static final String KEY = "hud.noellesroles.clone_doctor.";
    private static final CloneDoctorStateS2CPacket EMPTY = new CloneDoctorStateS2CPacket(false, 0, 0, 0, 0);
    private static CloneDoctorStateS2CPacket state = EMPTY;
    private static LimitedInventoryScreen returnScreen;
    private static Button returnButton;
    private static boolean registered;

    private CloneDoctorClient() {
    }

    /** 仅杀手及杀手方中立观察者看到彩虹色；其他人保留原色和原有透视权限。 */
    public static java.util.OptionalInt applyOutlineColor(net.minecraft.world.entity.Entity target,
            java.util.OptionalInt permittedColor) {
        if (permittedColor.isEmpty()) return permittedColor;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || SREClient.gameComponent == null
                || !SREClient.gameComponent.isKillerTeam(client.player)) return permittedColor;
        boolean clonedPerson = target instanceof net.minecraft.world.entity.player.Player player
                && org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorRole.isClone(player);
        return org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorOutlineColor.recolor(
                permittedColor, clonedPerson, net.minecraft.Util.getMillis());
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ModRoles.CLONE_DOCTOR.setInventoryScreenExtensionFactory(CloneDoctorRoleScreenExtension::new);
        ClientPlayNetworking.registerGlobalReceiver(CloneDoctorStateS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    state = new CloneDoctorStateS2CPacket(packet.isClone(), Math.max(0, packet.castTicks()),
                            Math.max(0, packet.transformTicks()), Math.max(0, packet.copyCooldown()),
                            Math.max(0, packet.substituteCooldown()));
                    if (context.client().screen instanceof LimitedInventoryScreen screen) {
                        updateReturnButton(screen);
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(CloneDoctorClient::tick);
        LimitedInventoryScreenEvents.INIT_TAIL.register(screen -> {
            returnScreen = screen;
            returnButton = null;
            updateReturnButton(screen);
        });
        LimitedInventoryScreenEvents.RENDER.register((screen, graphics, mouseX, mouseY, delta) ->
                updateReturnButton(screen));
        HudRenderCallback.EVENT.register((graphics, delta) -> renderHud(graphics));
    }

    public static CloneDoctorStateS2CPacket state() {
        return state;
    }

    /** 客户端仅预判；绝不扣金币或提前设置权威冷却。 */
    public static boolean canSelectTarget(UUID target) {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.level != null && SREClient.gameComponent != null
                && SREClient.gameComponent.isRunning()
                && SREClient.gameComponent.isRole(client.player, ModRoles.CLONE_DOCTOR)
                && GameUtils.isPlayerAliveAndSurvival(client.player)
                && state.castTicks() == 0 && state.transformTicks() == 0 && state.substituteCooldown() == 0
                && MoneyUtils.getBalance(client.player) >= COPY_COST
                && SREClient.gameComponent.isKillerTeam(target)
                && SREPlayerUtils.isPlayerAlive(client.level, target);
    }

    private static boolean canReturn() {
        Minecraft client = Minecraft.getInstance();
        return state.isClone() && state.transformTicks() > 0 && client.player != null
                && SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && GameUtils.isPlayerAliveAndSurvival(client.player);
    }

    private static void updateReturnButton(LimitedInventoryScreen screen) {
        if (returnScreen != screen) {
            returnScreen = screen;
            returnButton = null;
        }
        if (returnButton == null && canReturn()) {
            Component label = Component.translatable(KEY + "return");
            int width = Math.min(screen.width - 8, Math.max(120, Minecraft.getInstance().font.width(label) + 16));
            returnButton = screen.addRoleWidget(Button.builder(label, button -> {
                Minecraft client = Minecraft.getInstance();
                if (canReturn()) {
                    ClientPlayNetworking.send(new CloneDoctorActionC2SPacket(client.player.getUUID(), true));
                    screen.onClose();
                }
            }).bounds(4, screen.height - 24, width, 20).build());
        }
        if (returnButton != null) {
            returnButton.visible = canReturn();
            returnButton.active = returnButton.visible;
            if (returnButton.visible) {
                positionReturnButton(screen);
            }
        }
    }

    /** 固定底部居中，不再移到可能覆盖修饰符说明的顶部区域。 */
    private static void positionReturnButton(LimitedInventoryScreen screen) {
        returnButton.setPosition((screen.width - returnButton.getWidth()) / 2,
                Math.max(4, screen.height - returnButton.getHeight() - 6));
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            clear();
            return;
        }
        if (client.screen != returnScreen) {
            returnScreen = null;
            returnButton = null;
        }
        if (client.isPaused() || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || SREGameTimeComponent.KEY.get(client.level).isTimeFrozen()) {
            return;
        }
        if (state.castTicks() > 0 || state.transformTicks() > 0 || state.copyCooldown() > 0
                || state.substituteCooldown() > 0) {
            state = new CloneDoctorStateS2CPacket(state.isClone(), Math.max(0, state.castTicks() - 1),
                    Math.max(0, state.transformTicks() - 1), Math.max(0, state.copyCooldown() - 1),
                    Math.max(0, state.substituteCooldown() - 1));
        }
    }

    private static void renderHud(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui
                || SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()
                || !GameUtils.isPlayerAliveAndSurvival(client.player)) {
            return;
        }
        if (!state.isClone() && !SREClient.gameComponent.isRole(client.player, ModRoles.CLONE_DOCTOR)
                && state.castTicks() == 0 && state.transformTicks() == 0
                && state.copyCooldown() == 0 && state.substituteCooldown() == 0) {
            return;
        }
        int y = Math.max(4, graphics.guiHeight() / 2 - 48);
        if (state.castTicks() > 0) renderCastProgress(graphics);
        if (state.transformTicks() > 0) y = renderTransformationStatus(graphics, y);
        y = drawTimer(graphics, "copy_cooldown", state.copyCooldown(), y);
        drawTimer(graphics, "substitute_cooldown", state.substituteCooldown(), y);
    }

    /** 当前能力职业与恢复倒计时合并展示，不依赖复制职业自己的HUD。 */
    private static int renderTransformationStatus(GuiGraphics graphics, int y) {
        Minecraft client = Minecraft.getInstance();
        var role = SREClient.gameComponent.getRole(client.player);
        Component roleName = role == null
                ? Component.translatable("hud.noellesroles.clone_doctor.syncing_role")
                : org.agmas.noellesroles.utils.RoleUtils.getRoleName(role);
        Component title = Component.translatable(KEY + "current_role", roleName);
        Component timer = Component.translatable(KEY + "return_in", (state.transformTicks() + 19) / 20);
        int width = Math.min(260, graphics.guiWidth() - 12);
        var lines = client.font.split(title, width - 12);
        int lineHeight = client.font.lineHeight + 2;
        int height = (lines.size() + 1) * lineHeight + 10;
        graphics.fill(4, y - 4, 4 + width, y - 4 + height, 0xCC071813);
        graphics.fill(4, y - 4, 6, y - 4 + height, 0xFF69C6A1);
        int textY = y;
        for (var line : lines) {
            graphics.drawString(client.font, line, 10, textY, 0xFF9FF4D6, true);
            textY += lineHeight;
        }
        graphics.drawString(client.font, timer, 10, textY, 0xFFFFF4DC, true);
        return y - 4 + height + 4;
    }

    /** 前摇提示放在准星下方，避免被左侧其它职业HUD或聊天提示淹没。 */
    private static void renderCastProgress(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        int width = Math.min(180, graphics.guiWidth() - 16);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() / 2 + 24;
        Component label = Component.translatable(KEY + "cast", (state.castTicks() + 19) / 20);
        graphics.fill(x - 5, y - 4, x + width + 5, y + font.lineHeight + 14, 0xCC071813);
        graphics.drawCenteredString(font, label, graphics.guiWidth() / 2, y, 0xFF9FF4D6);
        int barY = y + font.lineHeight + 4;
        graphics.fill(x, barY, x + width, barY + 4, 0xFF234A3D);
        int elapsed = Math.clamp(20 - state.castTicks(), 0, 20);
        graphics.fill(x, barY, x + width * elapsed / 20, barY + 4, 0xFF69C6A1);
    }

    private static int drawTimer(GuiGraphics graphics, String key, int ticks, int y) {
        var font = Minecraft.getInstance().font;
        Component text = ticks <= 0
                ? Component.translatable(KEY + key.replace("_cooldown", "_ready"))
                : Component.translatable(KEY + key, ticks / 20 + (ticks % 20 > 0 ? 1 : 0));
        graphics.drawString(font, text, 6, y, ticks <= 0 ? 0xFF9FF4D6 : 0xFFFFF4DC, true);
        return y + font.lineHeight + 2;
    }

    private static void clear() {
        state = EMPTY;
        returnScreen = null;
        returnButton = null;
    }
}
