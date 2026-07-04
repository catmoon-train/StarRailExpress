package net.exmo.sre.camera.client;

import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import net.exmo.sre.camera.AdvancedCameraNode;
import net.exmo.sre.camera.AdvancedCameraSequence;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端高级相机导演。
 *
 * <p>负责接收一条 {@link AdvancedCameraSequence} 轨道并逐 tick 播放：解析为具体关键帧后，在帧渲染时按
 * tick + partialTick 插值出相机位置 / 朝向 / FOV，并由桥接 mixin 写入 Minecraft 的 {@code Camera}。
 *
 * <p>优先级：安全摄像头（{@link SecurityMonitorBlock#isInSecurityMode()}）高于高级相机；高级相机激活时
 * 才会接管视角，安全摄像头开启时本导演自动让位。
 *
 * <p>生命周期：在 {@code END_CLIENT_TICK} 调用 {@link #tick(Minecraft)} 推进；在断线 / 切世界 / 游戏结束时
 * 调用 {@link #clear()} 清理；在 HUD 回调里调用 {@link #renderOverlay(GuiGraphics)} 绘制黑边。
 * 轨道自然播放完毕时，镜头切回玩家瞬间会先全屏黑再渐显出画面（见 {@code RETURN_FADE_TICKS}）。
 */
public final class AdvancedCameraDirector {

    private static final Logger LOGGER = LoggerFactory.getLogger("SRE-AdvancedCamera");
    private static final float DEFAULT_FOV = 70.0f;
    /** 黑边渐入 / 渐出占整条轨道时长的比例。 */
    private static final float BAR_FADE_RATIO = 0.15f;
    /** 运镜自然结束、视角交还玩家后，全屏黑幕渐显（黑→透明）的时长（tick）。 */
    private static final int RETURN_FADE_TICKS = 10;

    @Nullable
    private static ActiveSequence active;

    @Nullable
    private static FixedOverride fixedOverride;

    /** 运镜结束回到玩家身上时的「黑屏渐显」过渡，独立于 {@link #active} 存活到淡出完成。 */
    @Nullable
    private static ReturnFade returnFade;

    private AdvancedCameraDirector() {
    }

    // ==================== 入口 ====================

    /** 由网络层调用：解析 JSON 并开始播放一条轨道。 */
    public static void play(String json) {
        try {
            start(AdvancedCameraSequence.fromJson(json));
        } catch (RuntimeException e) {
            LOGGER.warn("[AdvancedCamera] 无法解析轨道 JSON: {}", e.getMessage());
        }
    }

    /** 开始播放一条已解析的轨道。 */
    public static void start(AdvancedCameraSequence sequence) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || sequence.isEmpty()) {
            return;
        }
        Vec3 startPos = player.getEyePosition(1.0f);
        Keyframe[] frames = resolveFrames(sequence, startPos, player.getYRot(), player.getXRot());

        CameraType previousType = active != null
                ? active.previousType
                : fixedOverride != null ? fixedOverride.previousType : minecraft.options.getCameraType();
        // 切到第三人称，让相机脱离玩家头部（隐藏手部 / HUD），呈现自由运镜效果。
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        active = new ActiveSequence(sequence, frames, previousType);
        // 新轨道开始：取消上一条遗留的黑屏渐显。
        returnFade = null;
    }

    /** 清除当前轨道并按需恢复视角。 */
    public static void clear() {
        if (active == null) {
            return;
        }
        ActiveSequence finished = active;
        active = null;
        if (finished.sequence.restore && fixedOverride == null) {
            Minecraft.getInstance().options.setCameraType(finished.previousType);
        }
    }

    /** 设置一个由外部效果驱动的固定镜头。高级轨道播放时仍优先显示轨道镜头。 */
    public static void setFixedOverride(Vec3 pos, float yaw, float pitch, float fov) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        CameraType previousType = fixedOverride != null
                ? fixedOverride.previousType
                : active != null ? active.previousType : minecraft.options.getCameraType();
        if (minecraft.options.getCameraType() == CameraType.FIRST_PERSON) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        fixedOverride = new FixedOverride(pos, yaw, pitch, fov, previousType);
    }

    public static void clearFixedOverride() {
        if (fixedOverride == null) {
            return;
        }
        FixedOverride finished = fixedOverride;
        fixedOverride = null;
        if (active == null) {
            Minecraft.getInstance().options.setCameraType(finished.previousType);
        }
    }

    /**
     * 轨道自然播放完毕：交还视角，并对启用了电影黑边（{@code blackBars}）的轨道开启「黑屏渐显」过渡，
     * 让镜头切回玩家瞬间先全屏黑、再渐显出画面。被外部 {@link #clear()} 中断时不触发。
     */
    private static void finish() {
        boolean fade = active.sequence.blackBars;
        clear();
        if (fade) {
            returnFade = new ReturnFade();
        }
    }

    // ==================== 逐 tick 推进 ====================

    public static void tick(Minecraft minecraft) {
        // 推进黑屏渐显：它在运镜结束、active 被清空后仍需独立存活到淡出完成。
        if (returnFade != null && ++returnFade.ticks >= RETURN_FADE_TICKS) {
            returnFade = null;
        }
        if (minecraft.player == null || minecraft.level == null) {
            clearFixedOverride();
        }
        if (active == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        active.ticks++;
        if (!active.sequence.loop && active.ticks >= active.totalTicks) {
            // 自然播放完毕：交还视角并开启「黑屏渐显」过渡。
            finish();
        }
    }

    // ==================== 供桥接 mixin 查询 ====================

    /** 高级相机是否应当接管视角（激活且安全摄像头未开启）。 */
    public static boolean shouldOverride() {
        return (active != null || fixedOverride != null) && !SecurityMonitorBlock.isInSecurityMode();
    }

    public static Vec3 getCameraPos(float partialTick) {
        Pose pose = currentPose(partialTick);
        return pose == null ? Vec3.ZERO : pose.pos;
    }

    public static float getYaw(float partialTick) {
        Pose pose = currentPose(partialTick);
        return pose == null ? 0f : pose.yaw;
    }

    public static float getPitch(float partialTick) {
        Pose pose = currentPose(partialTick);
        return pose == null ? 0f : pose.pitch;
    }

    /**
     * 返回高级相机当前的 FOV 覆盖值（角度）。{@code <=0} 表示不覆盖。
     * 安全摄像头开启时不覆盖。
     */
    public static float getFovOverride(float partialTick) {
        if ((active == null && fixedOverride == null) || SecurityMonitorBlock.isInSecurityMode()) {
            return 0f;
        }
        Pose pose = currentPose(partialTick);
        return pose == null ? 0f : pose.fov;
    }

    private static float time(float partialTick) {
        return active.ticks + Mth.clamp(partialTick, 0f, 1f);
    }

    @Nullable
    private static Pose currentPose(float partialTick) {
        if (active != null) {
            return active.poseAt(time(partialTick));
        }
        if (fixedOverride != null) {
            return new Pose(fixedOverride.pos, fixedOverride.yaw, fixedOverride.pitch, fixedOverride.fov);
        }
        return null;
    }

    // ==================== 黑边渲染 ====================

    public static void renderOverlay(GuiGraphics guiGraphics) {
        // 回到玩家身上后的全屏黑幕渐显，绘制在最上层（覆盖黑边与画面）。
        renderReturnFade(guiGraphics);
        if (active == null || !active.sequence.blackBars) {
            return;
        }
        float alpha = active.barAlpha(time(0f));
        if (alpha <= 0f) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int barHeight = Math.max(18, Math.round(height * 0.11f));
        int color = ((int) (Mth.clamp(alpha, 0f, 1f) * 255f) << 24);
        guiGraphics.fill(0, 0, width, barHeight, color);
        guiGraphics.fill(0, height - barHeight, width, height, color);
    }

    /** 绘制运镜结束回到玩家身上时的全屏黑幕，按 tick + partialTick 由黑（alpha=1）渐显到透明。 */
    private static void renderReturnFade(GuiGraphics guiGraphics) {
        if (returnFade == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float progress = Mth.clamp((returnFade.ticks + partialTick) / RETURN_FADE_TICKS, 0f, 1f);
        float alpha = 1f - smoothstep(progress);
        if (alpha <= 0f) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        int color = ((int) (alpha * 255f) << 24);
        guiGraphics.fill(0, 0, width, height, color);
    }

    // ==================== 关键帧解析 ====================

    /**
     * 将轨道的节点解析为具体关键帧：补全继承的位置 / 角度，并把 lookAt 静态换算为 yaw/pitch。
     */
    private static Keyframe[] resolveFrames(AdvancedCameraSequence sequence, Vec3 startPos, float startYaw,
                                            float startPitch) {
        Keyframe[] frames = new Keyframe[sequence.nodes.size()];
        Vec3 prevPos = startPos;
        float prevYaw = startYaw;
        float prevPitch = startPitch;
        int arrival = 0;
        for (int i = 0; i < sequence.nodes.size(); i++) {
            AdvancedCameraNode node = sequence.nodes.get(i);
            Vec3 pos = node.pos != null ? node.pos : prevPos;

            float yaw;
            float pitch;
            if (node.lookAt != null) {
                Vec3 delta = node.lookAt.subtract(pos);
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                yaw = (float) (Math.atan2(delta.z, delta.x) * (180.0D / Math.PI)) - 90.0F;
                pitch = (float) (-(Math.atan2(delta.y, horizontal) * (180.0D / Math.PI)));
            } else {
                yaw = node.yaw != null ? node.yaw : prevYaw;
                pitch = node.pitch != null ? node.pitch : prevPitch;
            }

            arrival += node.durationTicks;
            frames[i] = new Keyframe(pos, yaw, pitch, node.fov, arrival, node.holdTicks);
            arrival += node.holdTicks;

            prevPos = pos;
            prevYaw = yaw;
            prevPitch = pitch;
        }
        return frames;
    }

    private static float smoothstep(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ==================== 内部状态 ====================

    /** 一个已解析的关键帧：到达时间 {@link #arrivalTick}，到达后停留 {@link #holdTicks}。 */
    private record Keyframe(Vec3 pos, float yaw, float pitch, float fov, int arrivalTick, int holdTicks) {
    }

    /** 相机当前帧的姿态。{@code fov<=0} 表示不覆盖。 */
    private record Pose(Vec3 pos, float yaw, float pitch, float fov) {
    }

    /** 运镜结束回到玩家身上的黑屏渐显状态：{@link #ticks} 自 0 累加到 {@link #RETURN_FADE_TICKS}。 */
    private static final class ReturnFade {
        int ticks;
    }

    private record FixedOverride(Vec3 pos, float yaw, float pitch, float fov, CameraType previousType) {
    }

    private static final class ActiveSequence {
        final AdvancedCameraSequence sequence;
        final Keyframe[] frames;
        final CameraType previousType;
        final int totalTicks;
        int ticks;

        ActiveSequence(AdvancedCameraSequence sequence, Keyframe[] frames, CameraType previousType) {
            this.sequence = sequence;
            this.frames = frames;
            this.previousType = previousType;
            this.totalTicks = Math.max(1, sequence.totalTicks());
        }

        float clampedTime(float t) {
            if (sequence.loop) {
                return t % totalTicks;
            }
            return Mth.clamp(t, 0f, totalTicks);
        }

        Pose poseAt(float rawTime) {
            float t = clampedTime(rawTime);
            Keyframe first = frames[0];
            // 第一节点到达前：停留在起始关键帧。
            if (t <= first.arrivalTick()) {
                return new Pose(first.pos(), first.yaw(), first.pitch(), first.fov());
            }
            for (int i = 1; i < frames.length; i++) {
                Keyframe to = frames[i];
                Keyframe from = frames[i - 1];
                int departFrom = from.arrivalTick() + from.holdTicks();
                if (t <= departFrom) {
                    // 停留在上一关键帧。
                    return new Pose(from.pos(), from.yaw(), from.pitch(), from.fov());
                }
                if (t <= to.arrivalTick()) {
                    int duration = to.arrivalTick() - departFrom;
                    float alpha = duration <= 0 ? 1f : smoothstep((t - departFrom) / (float) duration);
                    return interpolate(from, to, alpha);
                }
            }
            // 末尾停留在最后一个关键帧。
            Keyframe last = frames[frames.length - 1];
            return new Pose(last.pos(), last.yaw(), last.pitch(), last.fov());
        }

        private static Pose interpolate(Keyframe from, Keyframe to, float alpha) {
            Vec3 pos = new Vec3(
                    Mth.lerp(alpha, from.pos().x, to.pos().x),
                    Mth.lerp(alpha, from.pos().y, to.pos().y),
                    Mth.lerp(alpha, from.pos().z, to.pos().z));
            float yaw = Mth.rotLerp(alpha, from.yaw(), to.yaw());
            float pitch = Mth.lerp(alpha, from.pitch(), to.pitch());
            float fov;
            if (from.fov() <= 0 && to.fov() <= 0) {
                fov = 0f;
            } else {
                float a = from.fov() > 0 ? from.fov() : DEFAULT_FOV;
                float b = to.fov() > 0 ? to.fov() : DEFAULT_FOV;
                fov = Mth.lerp(alpha, a, b);
            }
            return new Pose(pos, yaw, pitch, fov);
        }

        float barAlpha(float rawTime) {
            float t = clampedTime(rawTime);
            if (sequence.loop) {
                return 1f;
            }
            float fade = Math.max(1f, totalTicks * BAR_FADE_RATIO);
            float fadeIn = Mth.clamp(t / fade, 0f, 1f);
            float fadeOut = Mth.clamp((totalTicks - t) / fade, 0f, 1f);
            return Math.min(fadeIn, fadeOut);
        }
    }
}
