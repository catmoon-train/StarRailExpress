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

/** 烟雾弹(60s版) — 投掷后产生烟雾区域，仅失明效果，无理智降低 */
public class SixtySecondsSmokeGrenadeItem extends SixtySecondsGrenadeItem {
    private static final double SMOKE_RADIUS = 4.0;

    public SixtySecondsSmokeGrenadeItem(Item.Properties properties) {
        super(properties, 0, 0, 0, false, false);
    }

    @Override
    public void explode(ServerLevel serverLevel, Vec3 impact, @Nullable ServerPlayer thrower) {
        AABB area = new AABB(impact, impact).inflate(SMOKE_RADIUS);
        for (ServerPlayer p : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                target -> target != thrower)) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, false, false));
        }
        for (int i = 0; i < 60; i++) {
            double ox = (serverLevel.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;
            double oy = serverLevel.random.nextDouble() * 3;
            double oz = (serverLevel.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    impact.x + ox, impact.y + oy, impact.z + oz,
                    3, 0.1, 0.1, 0.1, 0.03);
        }
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.5F, 0.5F);
    }
}
