package net.exmo.sre.meeting.client;

import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.exmo.sre.meeting.MeetingManager;
import net.exmo.sre.meeting.network.MeetingSpeakC2SPayload;
import net.exmo.sre.meeting.network.MeetingStateS2CPayload;
import net.exmo.sre.meeting.network.MeetingVoteResultS2CPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 紧急会议客户端：状态镜像、发言键、以及会议摄像机导演。
 *
 * 摄像机（基于 {@link AdvancedCameraDirector#setFixedOverride}，自带平滑插值）：
 * <ul>
 * <li>开场：环绕会议桌的半周下降运镜，配合 {@link MeetingHud} 的标题动画；</li>
 * <li>讨论·有人发言：镜头架在会议中心与发言者连线上，正对发言者；多人同时发言时
 * 自动拉远取所有发言者的中点取景；</li>
 * <li>讨论·无人发言：绕场缓慢巡航。</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class MeetingClientHandler {

    public static KeyMapping speakKey;

    // ── 同步状态（来自 MeetingStateS2CPayload）───────────────────
    public static int phase = MeetingManager.PHASE_NONE;
    public static Vec3 center = Vec3.ZERO;
    public static long phaseEndGameTime;
    public static String reporterName = "";
    public static String victimName = "";
    public static List<UUID> participants = List.of();
    public static List<UUID> speakers = List.of();
    /** 阶段切换时的墙钟毫秒，供 HUD 动画使用。 */
    public static long phaseChangeMillis;
    public static int lastPhase = MeetingManager.PHASE_NONE;
    /** 讨论阶段总时长（tick），供 HUD 进度条使用。 */
    public static long discussTotalTicks = 1;

    // ── 投票结果（来自 MeetingVoteResultS2CPayload）───────────────────
    public static boolean showVoteResult;
    public static String voteResultExpelledName = "";
    public static List<MeetingVoteResultS2CPayload.VoteEntry> voteResultEntries = List.of();
    public static long voteResultReceiveMillis;

    private static boolean overriding;
    private static boolean speakingToggled;

    private MeetingClientHandler() {
    }

    public static void register() {
        speakKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.starrailexpress.meeting_speak",
                GLFW.GLFW_KEY_B,
                "category.starrailexpress.general"));

        ClientPlayNetworking.registerGlobalReceiver(MeetingStateS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> applyState(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(MeetingVoteResultS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                showVoteResult = true;
                voteResultExpelledName = payload.expelledPlayerName();
                voteResultEntries = payload.voteEntries();
                voteResultReceiveMillis = Util.getMillis();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(MeetingClientHandler::tick);
    }

    private static void applyState(MeetingStateS2CPayload payload) {
        if (payload.phase() != phase) {
            lastPhase = phase;
            phaseChangeMillis = Util.getMillis();
            if (payload.phase() == MeetingManager.PHASE_DISCUSS
                    && Minecraft.getInstance().level != null) {
                discussTotalTicks = Math.max(1,
                        payload.phaseEndGameTime() - Minecraft.getInstance().level.getGameTime());
            }
        }
        phase = payload.phase();
        center = new Vec3(payload.centerX(), payload.centerY(), payload.centerZ());
        phaseEndGameTime = payload.phaseEndGameTime();
        reporterName = payload.reporterName();
        victimName = payload.victimName();
        participants = payload.participants();
        speakers = payload.speakers();
        if (phase == MeetingManager.PHASE_NONE) {
            speakingToggled = false;
            showVoteResult = false;
            voteResultEntries = List.of();
            stopOverride();
        }
    }

    public static boolean isSpeakingToggled() {
        return speakingToggled;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || phase == MeetingManager.PHASE_NONE) {
            stopOverride();
            return;
        }
        boolean participant = participants.contains(player.getUUID());

        // 发言键（仅讨论阶段的参会者）
        while (speakKey.consumeClick()) {
            if (phase == MeetingManager.PHASE_DISCUSS && participant) {
                speakingToggled = !speakingToggled;
                ClientPlayNetworking.send(new MeetingSpeakC2SPayload(speakingToggled));
            }
        }

        if (!participant) {
            stopOverride();
            return;
        }
        driveCamera(client, player);
    }

    private static void driveCamera(Minecraft client, LocalPlayer player) {
        long gameTime = client.level.getGameTime();
        Vec3 camPos;
        Vec3 lookTarget;
        float fov;

        if (phase == MeetingManager.PHASE_INTRO) {
            long remaining = Math.max(0, phaseEndGameTime - gameTime);
            float t = 1.0F - remaining / (float) MeetingManager.INTRO_TICKS;
            float eased = easeOutCubic(Mth.clamp(t, 0.0F, 1.0F));
            double angle = Math.PI * 0.25 + eased * Math.PI * 1.1;
            double radius = 10.5 - eased * 2.5;
            double height = 6.5 - eased * 3.2;
            camPos = center.add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            lookTarget = center.add(0, 1.2, 0);
            fov = 55.0F;
        } else {
            List<Player> speakingPlayers = resolveSpeakers(client);
            if (!speakingPlayers.isEmpty()) {
                Vec3 focus = Vec3.ZERO;
                for (Player speaker : speakingPlayers) {
                    focus = focus.add(speaker.getEyePosition());
                }
                focus = focus.scale(1.0 / speakingPlayers.size());
                double spread = 0;
                for (Player speaker : speakingPlayers) {
                    spread = Math.max(spread, speaker.getEyePosition().distanceTo(focus));
                }
                Vec3 fromCenter = focus.subtract(center.add(0, focus.y - center.y, 0));
                Vec3 dir = new Vec3(fromCenter.x, 0, fromCenter.z);
                if (dir.lengthSqr() < 0.01) {
                    dir = new Vec3(1, 0, 0);
                }
                dir = dir.normalize();
                double back = 3.2 + spread * 0.9;
                camPos = new Vec3(focus.x, 0, focus.z)
                        .subtract(dir.scale(back))
                        .add(0, focus.y + 0.35, 0);
                lookTarget = focus;
                fov = 48.0F;
            } else {
                double angle = gameTime * 0.006;
                camPos = center.add(Math.cos(angle) * 8.0, 3.4, Math.sin(angle) * 8.0);
                lookTarget = center.add(0, 1.1, 0);
                fov = 58.0F;
            }
        }

        Vec3 delta = lookTarget.subtract(camPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        AdvancedCameraDirector.setFixedOverride(camPos, yaw, Mth.clamp(pitch, -89.0F, 89.0F), fov);
        overriding = true;
    }

    private static List<Player> resolveSpeakers(Minecraft client) {
        List<Player> result = new ArrayList<>();
        for (UUID uuid : speakers) {
            Player speaker = client.level.getPlayerByUUID(uuid);
            if (speaker != null && speaker.isAlive()) {
                result.add(speaker);
            }
        }
        return result;
    }

    private static void stopOverride() {
        if (overriding) {
            overriding = false;
            AdvancedCameraDirector.clearFixedOverride();
        }
    }

    static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }
}
