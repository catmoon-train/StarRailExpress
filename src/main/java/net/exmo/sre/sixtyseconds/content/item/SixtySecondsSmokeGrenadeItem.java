package net.exmo.sre.sixtyseconds.content.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** 烟雾弹(60s版) — 严格复刻 noelles 烟雾弹的粒子生成机制：ServerSmokeAreaManager + ClientSmokeAreaManager */
public class SixtySecondsSmokeGrenadeItem extends SixtySecondsGrenadeItem {
    private static final double SMOKE_RADIUS = 4.0;
    private static final int SMOKE_DURATION_TICKS = 200; // 10秒

    public SixtySecondsSmokeGrenadeItem(Item.Properties properties) {
        super(properties, SMOKE_RADIUS, 0, 0, false, false);
    }

    @Override
    public void explode(ServerLevel serverLevel, Vec3 impact, @Nullable ServerPlayer thrower) {
        // 爆炸音效（与 noelles 一致）
        serverLevel.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.5F, 0.5F);

        // 创建持久烟雾区域（服务端施加失明 + 客户端渲染粒子）
        org.agmas.noellesroles.content.entity.ServerSmokeAreaManager.createSmokeArea(
                serverLevel, impact, SMOKE_RADIUS, SMOKE_DURATION_TICKS);

        // 初始粒子爆发（与 noelles SmokeGrenadeEntity 完全一致）
        for (int i = 0; i < 150; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;
            double offsetY = serverLevel.random.nextDouble() * 3;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * SMOKE_RADIUS * 2;

            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                    3, 0.1, 0.1, 0.1, 0.03);

            if (i % 3 == 0) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                        2, 0.2, 0.2, 0.2, 0.05);
            }
            if (i % 5 == 0) {
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        impact.x + offsetX, impact.y + offsetY, impact.z + offsetZ,
                        1, 0.15, 0.15, 0.15, 0.04);
            }
        }
    }
}
