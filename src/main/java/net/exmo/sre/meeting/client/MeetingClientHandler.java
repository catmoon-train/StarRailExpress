package net.exmo.sre.meeting.client;

import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.exmo.sre.meeting.MeetingManager;
import net.exmo.sre.meeting.network.MeetingSkipC2SPayload;
import net.exmo.sre.meeting.network.MeetingSkipStateS2CPayload;
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
import org.agmas.noellesroles.init.ModEffects;
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
 * 玩家持有 {@link ModEffects#TWO_DIMENSIONAL_CAMERA}（如鹅鸭杀模式全程俯视）时，相机由
 * {@code TwoDimensionalCameraClientHandle} 独占，本导演不接管。
 */
@Environment(EnvType.CLIENT)
public final class MeetingClientHandler {

    // 分号 [;/:] 键
    public static final KeyMapping speakKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.starrailexpress.meeting_speak",
            GLFW.GLFW_KEY_SEMICOLON,
            "category.starrailexpress.general"));;

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

    // ── 跳过会议（来自 MeetingSkipStateS2CPayload）───────────────────
    /** 本地玩家是否已投「跳过」（点击切换）。 */
    public static boolean skipVoted;
    /** 已投跳过的存活玩家数。 */
    public static int skipCount;
    /** 场上存活玩家总数（阈值 = 超过 aliveCount/2）。 */
    public static int skipAliveCount;

    private static boolean overriding;
    private static boolean speakingToggled;
    /** 跳过按钮左键按下沿检测。 */
    private static boolean wasLeftDownSkip;

    private MeetingClientHandler() {
    }

    public static void register() {
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
        ClientPlayNetworking.registerGlobalReceiver(MeetingSkipStateS2CPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                skipCount = payload.skipCount();
                skipAliveCount = payload.aliveCount();
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
            skipVoted = false;
            skipCount = 0;
            skipAliveCount = 0;
            stopOverride();
        } else if (payload.phase() == MeetingManager.PHASE_INTRO) {
            // 新会议开始：重置本地跳过投票状态
            skipVoted = false;
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

        // 跳过会议按钮（开场 / 讨论阶段的参会者，左键点击切换）
        boolean canSkip = participant
                && (phase == MeetingManager.PHASE_INTRO || phase == MeetingManager.PHASE_DISCUSS);
        boolean leftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                client.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (canSkip && leftDown && !wasLeftDownSkip) {
            int[] r = skipButtonRect();
            double mx = client.mouseHandler.xpos();
            double my = client.mouseHandler.ypos();
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                skipVoted = !skipVoted;
                ClientPlayNetworking.send(new MeetingSkipC2SPayload(skipVoted));
            }
        }
        wasLeftDownSkip = leftDown;

        if (!participant) {
            stopOverride();
            return;
        }
        // 2D 视角（鹅鸭杀全程俯视）自己就在每 tick 写 fixedOverride。两边同时写会让导演在两套
        // 完全不同的机位之间来回插值，画面剧烈抖动，所以此时会议镜头整体让位。
        // 注意不能调 stopOverride()：那会清掉 2D 相机的 override。overriding 恒为 false，
        // 故散会时的 stopOverride() 也自然是空操作。
        if (player.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA)) {
            overriding = false;
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

    /** 跳过会议按钮矩形（GUI 坐标）：物品栏正上方居中。返回 {x, y, w, h}。 */
    public static int[] skipButtonRect() {
        Minecraft client = Minecraft.getInstance();
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        int bw = 150;
        int bh = 20;
        return new int[] { (w - bw) / 2, h - 70, bw, bh };
    }
}
