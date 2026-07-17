package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.exmo.sre.sixtyseconds.content.entity.ServerMarkingAreaManager;
import org.jetbrains.annotations.Nullable;

/** 标记弹 — 投掷后产生白色持久烟雾区域，进入范围的玩家获得发光效果 40 秒 */
public class SixtySecondsMarkingGrenadeItem extends SixtySecondsGrenadeItem {
    private static final double MARK_RADIUS = 2.5;
    private static final int MARK_DURATION_TICKS = 20 * 40; // 40 秒

    public SixtySecondsMarkingGrenadeItem(Item.Properties properties) {
        super(properties, MARK_RADIUS, 0, 0, false, false);
    }

    @Override
    public void explode(ServerLevel serverLevel, Vec3 impact, @Nullable ServerPlayer thrower) {
        // 爆炸音效
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.2F, 1.2F);

        // 创建持久标记区域（服务端施加发光 + 客户端渲染白色粒子）
        ServerMarkingAreaManager.createMarkingArea(serverLevel, impact, MARK_RADIUS, MARK_DURATION_TICKS);

        // 初始白色粒子爆发（与 noelles SmokeGrenadeEntity 结构一致，粒子类型改为白色）
        for (int i = 0; i < 150; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * MARK_RADIUS * 2;
            double offsetY = serverLevel.random.nextDouble() * 3;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * MARK_RADIUS * 2;

            serverLevel.sendParticles(ParticleTypes.WHITE_SMOKE,
                    impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                    3, 0.1, 0.1, 0.1, 0.03);

            if (i % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                        2, 0.2, 0.2, 0.2, 0.05);
            }
            if (i % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.EFFECT,
                        impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                        1, 0.15, 0.15, 0.15, 0.04);
            }
        }
    }
}
