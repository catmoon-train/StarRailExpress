/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.planecrash.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 飞机事件的客户端反馈：镜头抖动 / 倾泻、玩家模型统一倾斜、碎玻璃与倒塌音效。
 */
public final class PlaneCrashClientEffects {
    private static final float MAX_ROLL = 11.0F;
    private static final float MAX_LEAN = 16.0F;

    private static int tremorTicks;
    private static int tremorDuration;
    private static float tiltYaw;

    private PlaneCrashClientEffects() {
    }

    public static void startTremor(float yaw, int durationTicks) {
        tremorDuration = Math.max(8, durationTicks);
        tremorTicks = tremorDuration;
        tiltYaw = yaw;
        playTremorSounds();
        spawnDust();
    }

    public static void tick() {
        if (tremorTicks > 0) {
            tremorTicks--;
        }
    }

    public static void clear() {
        tremorTicks = 0;
        tremorDuration = 0;
    }

    public static boolean isTremorActive() {
        return tremorTicks > 0;
    }

    /** 由 Camera mixin 调用：返回抖动偏移，并直接把世界倾泻写进相机四元数。 */
    public static CameraJitter computeCamera(Camera camera, float tickDelta) {
        if (tremorTicks <= 0) {
            return CameraJitter.NONE;
        }
        float intensity = intensity(tickDelta);
        float yawJitter = 0.0F;
        float pitchJitter = 0.0F;
        double ox = 0.0D;
        double oy = 0.0D;
        double oz = 0.0D;
        if (!SREClientConfig.instance().disableScreenShake) {
            float t = tremorDuration - tremorTicks + tickDelta;
            yawJitter = Mth.sin(t * 1.7F) * 1.35F * intensity;
            pitchJitter = Mth.cos(t * 2.1F) * 1.05F * intensity;
            ox = Mth.sin(t * 2.4F) * 0.08F * intensity;
            oy = Mth.cos(t * 1.9F) * 0.05F * intensity;
            oz = Mth.sin(t * 1.3F) * 0.08F * intensity;
        }
        float worldRoll = worldSpaceRoll(camera.getYRot()) * MAX_ROLL * intensity;
        return new CameraJitter(yawJitter, pitchJitter, ox, oy, oz, worldRoll);
    }

    public static void applyRoll(Quaternionf rotation, float rollDegrees) {
        if (rollDegrees != 0.0F) {
            rotation.rotateZ(rollDegrees * Mth.DEG_TO_RAD);
        }
    }

    public record CameraJitter(float yaw, float pitch, double x, double y, double z, float roll) {
        static final CameraJitter NONE = new CameraJitter(0, 0, 0, 0, 0, 0);
    }

    public static void applyPlayerLean(PoseStack poseStack, LivingEntity entity, float tickDelta) {
        if (tremorTicks <= 0) {
            return;
        }
        float lean = MAX_LEAN * intensity(tickDelta);
        if (lean < 0.05F) {
            return;
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(-tiltYaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(lean));
        poseStack.mulPose(Axis.YP.rotationDegrees(tiltYaw));
    }

    private static float intensity(float tickDelta) {
        float remaining = tremorTicks - tickDelta;
        float fadeIn = Mth.clamp((tremorDuration - remaining) / 4.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(remaining / 10.0F, 0.0F, 1.0F);
        return fadeIn * fadeOut;
    }

    /**
     * 把世界固定倾泻方向换算成镜头滚转：所有玩家看到的“地面倾斜”朝同一边。
     */
    private static float worldSpaceRoll(float cameraYaw) {
        double tiltRad = Math.toRadians(tiltYaw);
        double tx = -Math.sin(tiltRad);
        double tz = Math.cos(tiltRad);
        double camRad = Math.toRadians(cameraYaw);
        double rx = -Math.cos(camRad);
        double rz = -Math.sin(camRad);
        return (float) (tx * rx + tz * rz);
    }

    private static void playTremorSounds() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        player.playNotifySound(TMMSounds.EVENT_PLANE_CRASH_GLASS, SoundSource.AMBIENT, 1.15F, 0.85F);
        player.playNotifySound(TMMSounds.EVENT_PLANE_CRASH_COLLAPSE, SoundSource.AMBIENT, 1.05F, 0.7F);
        player.playNotifySound(SoundEvents.GLASS_BREAK, SoundSource.AMBIENT, 1.0F, 0.65F);
        player.playNotifySound(SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.AMBIENT, 0.85F, 0.4F);
        player.playNotifySound(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.AMBIENT, 0.35F, 0.45F);
    }

    private static void spawnDust() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Vec3 pos = player.position();
        for (int i = 0; i < 18; i++) {
            minecraft.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.x + (player.getRandom().nextDouble() - 0.5D) * 4.0D,
                    pos.y + player.getRandom().nextDouble() * 1.6D,
                    pos.z + (player.getRandom().nextDouble() - 0.5D) * 4.0D,
                    0.0D, 0.03D, 0.0D);
        }
    }
}
