package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.event.AllowOtherCameraType;
import net.exmo.sre.camera.client.AdvancedCameraDirector;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 二维视角（{@link ModEffects#TWO_DIMENSIONAL_CAMERA}）客户端相机。
 *
 * <p><b>遮挡剔除一律走箱庭视野</b>（{@link HakoniwaVisionClientHandle}，区块网格级逐块剔除），
 * 二维视角激活时它会自动启用，无需再单独授予 {@link ModEffects#HAKONIWA_VISION}。
 *
 * <p>历史上这里还有一条「把近裁剪面推到房间近墙外」的退路，已删除：近裁剪面垂直于视线，
 * 而墙是竖直平面 —— 2.5D / 侧视有俯角时二者不平行，墙的下半截必然落在裁剪面之后活下来，
 * 正好挡住玩家。此时玩家本体被深度测试挡掉，而透视高亮的描边（{@code RenderType.outline}
 * 带 {@code NO_DEPTH_TEST}）却照画不误，症状就是「看不见模型，只看得到一圈发光边缘」。
 */
public final class TwoDimensionalCameraClientHandle {
    /** 正上方俯视：相机在玩家头顶垂直下望（pitch = 90°），区别于 0~3 的 2.5D 俯视与 5~8 的纯侧视。 */
    public static final int TOP_VIEW_AMPLIFIER = 4;
    private static final double DEFAULT_CAMERA_DISTANCE = 28.0D;
    private static final double CAMERA_HEIGHT = 6.0D;
    private static final double TOP_CAMERA_HEIGHT = 34.0D;
    private static final float CAMERA_FOV = 35.0F;
    private static volatile boolean active;
    private static volatile Vec3 listenerPosition;
    private static volatile float cameraYaw;
    private static volatile boolean topView;
    private static volatile Vec3 cameraPosition;
    /** 本导演是否正持有 {@link AdvancedCameraDirector} 的固定镜头。 */
    private static boolean ownsOverride;

    private TwoDimensionalCameraClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TwoDimensionalCameraClientHandle::tick);
        AllowOtherCameraType.EVENT.register((original, localPlayer) -> {
            if (isLocalTwoDimensionalActive(localPlayer)) {
                return AllowOtherCameraType.ReturnCameraType.THIRD_PERSON_BACK;
            }
            return AllowOtherCameraType.ReturnCameraType.NO_CHANGE;
        });
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            deactivate();
            releaseOverride();
            return;
        }

        MobEffectInstance effect = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        if (effect == null) {
            deactivate();
            releaseOverride();
            return;
        }

        active = true;
        topView = effect.getAmplifier() == TOP_VIEW_AMPLIFIER;
        listenerPosition = player.getEyePosition(1.0F);
        Vec3 lookAt = player.getEyePosition(1.0F).add(0.0D, 0.5D, 0.0D);
        Vec3 cameraPos = cameraPosition(lookAt, effect.getAmplifier(), cameraDistance(player));
        cameraPosition = cameraPos;
        Vec3 delta = lookAt.subtract(cameraPos);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) (-(Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG));
        cameraYaw = yaw;
        AdvancedCameraDirector.setFixedOverride(cameraPos, yaw, pitch, CAMERA_FOV);
        ownsOverride = true;
    }

    private static void deactivate() {
        active = false;
        topView = false;
        listenerPosition = null;
        cameraPosition = null;
    }

    /**
     * 只归还本导演自己设过的固定镜头。固定镜头是 {@link AdvancedCameraDirector} 的全局单槽，
     * 无条件 clear 会把别的持有者（如会议镜头 {@code MeetingClientHandler}）一起清掉。
     */
    private static void releaseOverride() {
        if (ownsOverride) {
            ownsOverride = false;
            AdvancedCameraDirector.clearFixedOverride();
        }
    }

    private static boolean isLocalTwoDimensionalActive(LocalPlayer localPlayer) {
        return localPlayer != null && localPlayer.hasEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
    }

    private static double cameraDistance(LocalPlayer player) {
        MobEffectInstance distanceEffect = player.getEffect(ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE);
        if (distanceEffect == null) {
            return DEFAULT_CAMERA_DISTANCE;
        }
        return ModEffects.getTwoDimensionalCameraDistance(distanceEffect.getAmplifier());
    }

    private static Vec3 cameraPosition(Vec3 lookAt, int amplifier, double cameraDistance) {
        if (amplifier == TOP_VIEW_AMPLIFIER) {
            double height = Math.max(CAMERA_HEIGHT + 2.0D,
                    cameraDistance + (TOP_CAMERA_HEIGHT - DEFAULT_CAMERA_DISTANCE));
            return lookAt.add(0.0D, height, 0.0D);
        }
        if (amplifier >= 5) {
            // 5~8：东西南北平面视线 —— 相机与视点同高的纯侧视（无俯角），区别于 0~3 的 2.5D 俯视
            return lookAt.add(sideVector(amplifier - 5).scale(cameraDistance));
        }
        return lookAt.add(sideVector(amplifier).scale(cameraDistance)).add(0.0D, CAMERA_HEIGHT, 0.0D);
    }

    private static Vec3 sideVector(int amplifier) {
        return switch (Mth.clamp(amplifier, 0, 3)) {
            case 0 -> new Vec3(-1.0D, 0.0D, 0.0D); // 西边
            case 1 -> new Vec3(1.0D, 0.0D, 0.0D);  // 东边
            case 2 -> new Vec3(0.0D, 0.0D, -1.0D); // 北边
            default -> new Vec3(0.0D, 0.0D, 1.0D); // 南边
        };
    }

    public static boolean isActive() {
        return active;
    }

    /**
     * 当前二维相机的水平偏航角。把它当作偏航角走 {@code getInputVector} 时，W 恰好是屏幕正上方：
     * 侧视 / 2.5D 俯视下它就是镜头的水平朝向；amplifier 4 的纯俯视（pitch = 90°）下相机的
     * up 向量退化为该偏航角的水平前向，结论同样成立。
     */
    public static float cameraYaw() {
        return cameraYaw;
    }

    /** 当前是否为正上方俯视（amplifier {@value #TOP_VIEW_AMPLIFIER}）。 */
    public static boolean isTopView() {
        return active && topView;
    }

    /** 当前二维相机的世界坐标；未激活时为 null。 */
    public static Vec3 cameraPosition() {
        return active ? cameraPosition : null;
    }

    /**
     * 二维视角下「耳朵」所在的世界坐标（玩家眼睛），未激活时为 null。
     *
     * <p>相机被架到玩家上方 / 侧面十几到几十格外，而 OpenAL 监听者、simple voice chat 的参考点
     * 默认都取相机位置 —— 于是脚边的脚步声、几格外的说话声都被按「几十格外」做距离衰减，
     * 直接衰减到听不见（原版音效默认衰减距离仅 16 格）。监听位置必须回到玩家身上。
     * 朝向仍取相机的：这样左右声道与屏幕方向一致。
     */
    public static Vec3 listenerPosition() {
        return active ? listenerPosition : null;
    }
}
