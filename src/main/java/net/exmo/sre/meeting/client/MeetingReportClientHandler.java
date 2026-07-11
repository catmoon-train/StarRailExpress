package net.exmo.sre.meeting.client;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.exmo.sre.meeting.MeetingManager;
import net.exmo.sre.meeting.network.MeetingCooldownS2CPayload;
import net.exmo.sre.meeting.network.MeetingReportC2SPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.UUID;

/**
 * 「瞄准尸体按键召开会议」客户端：仅在地图配置启用会议（{@code meetingEnabled}）
 * 且游戏运行中时，准星指向尸体会在准星下方提示按键；冷却中（开局冷却本地算，
 * 会议间冷却由 {@link MeetingCooldownS2CPayload} 同步）则显示剩余秒数。
 */
@Environment(EnvType.CLIENT)
public final class MeetingReportClientHandler {

    // 小键盘 [-] 键（主键盘 - 已被地图介绍键占用）
    public static final KeyMapping reportKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.meeting_report",
            GLFW.GLFW_KEY_KP_SUBTRACT,
            "category.starrailexpress.general"));

    // 配色对齐 MeetingHud 的复古车票风格
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;

    /** 会议间冷却结束的世界 gameTime（服务端散会时下发），0 表示无冷却。 */
    private static long cooldownEndGameTime;
    /** 已上报过的尸体 UUID（同一具尸体不能再召开会议）。 */
    private static Set<UUID> reportedBodies = Set.of();

    private MeetingReportClientHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MeetingCooldownS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    cooldownEndGameTime = payload.cooldownEndGameTime();
                    reportedBodies = Set.copyOf(payload.reportedBodies());
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            cooldownEndGameTime = 0;
            reportedBodies = Set.of();
        });
        ClientTickEvents.END_CLIENT_TICK.register(MeetingReportClientHandler::tick);
        HudRenderCallback.EVENT.register(MeetingReportClientHandler::renderHint);
    }

    private static void tick(Minecraft client) {
        while (reportKey.consumeClick()) {
            if (!canPrompt(client)) {
                continue;
            }
            PlayerBodyEntity body = targetedBody(client);
            if (body == null || reportedBodies.contains(body.getUUID())
                    || cooldownRemainingTicks(client) > 0) {
                continue;
            }
            ClientPlayNetworking.send(new MeetingReportC2SPayload(body.getId()));
        }
    }

    private static void renderHint(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || !canPrompt(client)) {
            return;
        }
        PlayerBodyEntity body = targetedBody(client);
        if (body == null) {
            return;
        }

        Component text;
        int color;
        if (reportedBodies.contains(body.getUUID())) {
            text = Component.translatable("meeting.sre.report_already");
            color = MUTED;
        } else {
            long remain = cooldownRemainingTicks(client);
            if (remain > 0) {
                text = Component.translatable("meeting.sre.report_cooldown", (remain + 19) / 20);
                color = MUTED;
            } else {
                text = Component.translatable("meeting.sre.report_hint",
                        reportKey.getTranslatedKeyMessage());
                color = TEXT;
            }
        }
        g.drawCenteredString(client.font, text, g.guiWidth() / 2, g.guiHeight() / 2 + 16, color);
    }

    /** 地图启用会议 + 游戏运行中 + 当前无会议 + 本人非旁观。 */
    private static boolean canPrompt(Minecraft client) {
        if (client.player == null || client.level == null || client.player.isSpectator()) {
            return false;
        }
        if (MeetingClientHandler.phase != MeetingManager.PHASE_NONE) {
            return false;
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.getNullable(client.level);
        if (areas == null || !areas.areasSettings.meetingEnabled) {
            return false;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.getNullable(client.level);
        return game != null && game.isRunning();
    }

    private static PlayerBodyEntity targetedBody(Minecraft client) {
        if (client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerBodyEntity body && body.isAlive()) {
            return body;
        }
        return null;
    }

    /** 剩余冷却 tick：取「会议间冷却」与「开局冷却」的较大者。 */
    private static long cooldownRemainingTicks(Minecraft client) {
        long remain = Math.max(0, cooldownEndGameTime - client.level.getGameTime());
        AreasWorldComponent areas = AreasWorldComponent.KEY.getNullable(client.level);
        if (areas != null && areas.areasSettings.meetingStartCooldown > 0) {
            SREGameTimeComponent time = SREGameTimeComponent.KEY.getNullable(client.level);
            if (time != null) {
                long elapsed = Math.max(0, time.getResetTime() - time.getTime());
                remain = Math.max(remain, areas.areasSettings.meetingStartCooldown * 20L - elapsed);
            }
        }
        return remain;
    }
}
