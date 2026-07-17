package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 标记弹 — 投掷后白色烟雾，进入范围的玩家获得发光效果持续40秒 */
public class SixtySecondsMarkingGrenadeItem extends SixtySecondsGrenadeItem {
    private static final double MARK_RADIUS = 2.5;

    public SixtySecondsMarkingGrenadeItem(Item.Properties properties) {
        super(properties, 0, 0, 0, false, false);
    }

    @Override
    public void explode(ServerLevel serverLevel, Vec3 impact, @Nullable ServerPlayer thrower) {
        AABB area = new AABB(impact, impact).inflate(MARK_RADIUS);
        for (ServerPlayer p : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                target -> target != thrower)) {
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 40, 0, false, false));
        }
        // 白色粒子
        for (int i = 0; i < 40; i++) {
            double ox = (serverLevel.random.nextDouble() - 0.5) * MARK_RADIUS * 2;
            double oy = serverLevel.random.nextDouble() * 2.5;
            double oz = (serverLevel.random.nextDouble() - 0.5) * MARK_RADIUS * 2;
            serverLevel.sendParticles(ParticleTypes.WHITE_SMOKE,
                    impact.x + ox, impact.y + oy, impact.z + oz,
                    2, 0.15, 0.15, 0.15, 0.04);
        }
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.2F, 1.2F);
    }
}
