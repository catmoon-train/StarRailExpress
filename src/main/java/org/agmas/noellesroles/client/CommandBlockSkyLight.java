package org.agmas.noellesroles.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 命令方块事件的褪色紫天光。做法对齐 ExWorld 夜空红光：染天空穹顶、雾、天光向量和月亮，
 * 不用深度遮罩后处理，避免地平线撕边。事件进行时全天生效，不只在夜里。
 */
public final class CommandBlockSkyLight {
    private static final float SKY_LIGHT_BLEND = 0.74F;
    private static final Vector3f SKY_LIGHT_COLOR = new Vector3f(0.46F, 0.09F, 0.60F);
    private static final float DOME_BLEND = 0.64F;
    private static final float DOME_RED = 0.33F;
    private static final float DOME_GREEN = 0.07F;
    private static final float DOME_BLUE = 0.46F;
    private static final float MOON_BLEND = 0.68F;
    private static final float MOON_RED = 0.74F;
    private static final float MOON_GREEN = 0.36F;
    private static final float MOON_BLUE = 0.94F;


    private CommandBlockSkyLight() {
    }

    /** 只跟进度包走。看见残留方块不算事件已开，避免没开局就染天。 */
    public static boolean active() {
        return CommandBlockProgressClient.isShowing();
    }

    public static boolean overworldSky(ClientLevel level) {
        return level != null && level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
    }

    public static void tintSkyLight(Vector3f skyVector) {
        if (!active() || skyVector == null) {
            return;
        }
        skyVector.lerp(SKY_LIGHT_COLOR, SKY_LIGHT_BLEND);
    }

    public static Vec3 tintDome(Vec3 color) {
        if (!active() || color == null) {
            return color;
        }
        return color.lerp(new Vec3(DOME_RED, DOME_GREEN, DOME_BLUE), DOME_BLEND);
    }

    public static void tintFog(float red, float green, float blue, float[] out) {
        out[0] = Mth.lerp(DOME_BLEND, red, DOME_RED);
        out[1] = Mth.lerp(DOME_BLEND, green, DOME_GREEN);
        out[2] = Mth.lerp(DOME_BLEND, blue, DOME_BLUE);
    }

    public static Vector3f moonColor() {
        return new Vector3f(
                Mth.lerp(MOON_BLEND, 1.0F, MOON_RED),
                Mth.lerp(MOON_BLEND, 1.0F, MOON_GREEN),
                Mth.lerp(MOON_BLEND, 1.0F, MOON_BLUE));
    }
}
