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

    private CloneDoctorClient() {
    }

    public static void register() {
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

    /** 优先底边空白，再尝试顶边；避开已有控件，保留中央的复制职业界面与物品栏。 */
    private static void positionReturnButton(LimitedInventoryScreen screen) {
        for (int y : new int[] { screen.height - 24, 4 }) {
            for (int x = 4; x + returnButton.getWidth() <= screen.width - 4; x += 4) {
                boolean occupied = false;
                for (var child : screen.children()) {
                    if (child instanceof AbstractWidget widget && widget != returnButton && widget.visible
                            && x < widget.getX() + widget.getWidth() + 2
                            && x + returnButton.getWidth() + 2 > widget.getX()
                            && y < widget.getY() + widget.getHeight() + 2 && y + 22 > widget.getY()) {
                        occupied = true;
                        break;
                    }
                }
                if (!occupied) {
                    returnButton.setPosition(x, y);
                    return;
                }
            }
        }
        returnButton.visible = false;
        returnButton.active = false;
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
        if (state.castTicks() > 0) y = drawTimer(graphics, "cast", state.castTicks(), y);
        if (state.transformTicks() > 0) y = drawTimer(graphics, "transformed", state.transformTicks(), y);
        y = drawTimer(graphics, "copy_cooldown", state.copyCooldown(), y);
        drawTimer(graphics, "substitute_cooldown", state.substituteCooldown(), y);
    }

    private static int drawTimer(GuiGraphics graphics, String key, int ticks, int y) {
        var font = Minecraft.getInstance().font;
        Component text = Component.translatable(KEY + key, ticks / 20 + (ticks % 20 > 0 ? 1 : 0));
        graphics.drawString(font, text, 6, y, 0xFFFFF4DC, true);
        return y + font.lineHeight + 2;
    }

    private static void clear() {
        state = EMPTY;
        returnScreen = null;
        returnButton = null;
    }
}
