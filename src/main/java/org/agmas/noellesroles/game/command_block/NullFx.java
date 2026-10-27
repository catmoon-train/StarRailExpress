package org.agmas.noellesroles.game.command_block;

import org.agmas.noellesroles.Noellesroles;

import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Null 的「注销」特效。
 * 只发一个自定义粒子包，形状由客户端 {@code NullRiftClientFx} 自己摆，不逐点发包。
 */
public final class NullFx {
    public static final ResourceLocation RIFT = Noellesroles.id("null_rift");

    /** 前摇标记：目标脚下的一圈灰白环。参数 0 = 半径。 */
    public static final float MARK = 0.0F;
    /** 抹除命中：三重扩散环 + 灵魂柱 + 白闪。参数 0 = 半径。 */
    public static final float ERASE = 1.0F;
    /** 涂黑：一圈电弧。参数 0 = 半径。 */
    public static final float REDACT = 2.0F;

    private NullFx() {
    }

    /** 抹除前摇：在目标脚下点亮一圈「已注销」的光。 */
    public static void mark(ServerLevel level, Vec3 at) {
        ParticleFx.sendCustom(level, RIFT, at, 10, 1.3F, MARK);
    }

    /** 抹除命中：目标位置炸开。 */
    public static void erase(ServerLevel level, Vec3 at) {
        ParticleFx.sendCustom(level, RIFT, at, 30, 2.1F, ERASE);
    }

    /** 涂黑：以自己为圆心拉出一圈电弧。 */
    public static void redact(ServerLevel level, Vec3 at, float radius) {
        ParticleFx.sendCustom(level, RIFT, at, 30, Math.max(1.5F, radius), REDACT);
    }
}
