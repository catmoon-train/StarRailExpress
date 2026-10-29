package org.agmas.noellesroles.game.halloween.ambience;

import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 与命令方块同走穹顶、清屏雾、月亮和天光向量，不使用 HUD 遮罩或修改地图灯。 */
@Environment(EnvType.CLIENT)
public final class HalloweenAmbienceSky {
    private static final Vec3 ORANGE = new Vec3(1.0, .35, .08);
    // ltsc-master 没有命令方块事件：仅探测可选客户端入口，不链接 Director 或事件类。
    private static final BooleanSupplier PURPLE_EVENT = optionalFlag(
            "org.agmas.noellesroles.client.CommandBlockSkyLight", "active");
    private static final BooleanSupplier BLACK_FOG = optionalFlag(
            "org.agmas.noellesroles.client.YouluFreeCamClient", "shouldBlackenFog");

    private HalloweenAmbienceSky() {}

    public static boolean active(ClientLevel level) {
        return level != null && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL
                && HalloweenAmbienceClient.active(level) && !PURPLE_EVENT.getAsBoolean();
    }

    public static boolean fogActive(Camera camera, ClientLevel level) {
        if (!active(level) || camera.getFluidInCamera() != FogType.NONE || BLACK_FOG.getAsBoolean()) return false;
        return !(camera.getEntity() instanceof LivingEntity living)
                || (!living.hasEffect(MobEffects.BLINDNESS) && !living.hasEffect(MobEffects.DARKNESS));
    }

    public static Vec3 tintDome(Vec3 color) {
        return color.lerp(ORANGE, .60);
    }

    /** 保留原雾亮度，黑暗效果不会被固定的橙色亮度抬亮。 */
    public static float[] tintFog(float red, float green, float blue) {
        float brightness = Math.max(red, Math.max(green, blue));
        return new float[] {red * .45F + brightness * .55F,
                green * .45F + brightness * .35F * .55F,
                blue * .45F + brightness * .08F * .55F};
    }

    /** 仅染原版最初的天光向量，后续关灯、直觉和亡魂夜像素缩放照常执行。 */
    public static void tintSkyLight(Vector3f color) {
        color.mul(1.0F, .70F, .55F);
    }

    private static BooleanSupplier optionalFlag(String className, String methodName) {
        try {
            Method method = Class.forName(className, false, HalloweenAmbienceSky.class.getClassLoader())
                    .getMethod(methodName);
            return () -> {
                try {
                    return Boolean.TRUE.equals(method.invoke(null));
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // 可选事件存在但 API 不兼容时让出颜色控制，不压掉高优先效果。
                    return true;
                }
            };
        } catch (ClassNotFoundException ignored) {
            return () -> false;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return () -> true;
        }
    }
}
