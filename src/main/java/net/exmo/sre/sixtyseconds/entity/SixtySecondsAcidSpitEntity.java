package net.exmo.sre.sixtyseconds.entity;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 吐酸者的酸液投射物：命中玩家扣 {@link SixtySecondsBalance#PVE_SPIT_INJURY} 健康 + 少量污染；
 * 命中方块/超时消散。客户端用 {@code ThrownItemRenderer}（史莱姆球外观 + 绿色粒子尾迹）。
 */
public class SixtySecondsAcidSpitEntity extends ThrowableItemProjectile {

    public SixtySecondsAcidSpitEntity(EntityType<? extends SixtySecondsAcidSpitEntity> entityType, Level level) {
        super(entityType, level);
    }

    public SixtySecondsAcidSpitEntity(Level level, LivingEntity shooter) {
        super(ModEntities.SIXTY_SECONDS_ACID_SPIT, shooter, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SLIME_BALL;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
        } else if (tickCount > 20 * 5) {
            discard(); // 超时消散（防越界飞行）
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel serverLevel) || !SixtySecondsMod.isActive(serverLevel)) {
            return;
        }
        if (result.getEntity() instanceof ServerPlayer player && SixtySecondsMonsterEntity.isValidPrey(player)) {
            SixtySecondsHealthSystem.applyInjury(player, null, SixtySecondsBalance.PVE_SPIT_INJURY);
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + SixtySecondsBalance.PVE_SPIT_POLLUTION);
            stats.sync();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            playSound(SoundEvents.SLIME_BLOCK_BREAK, 0.6F, 1.4F);
            discard();
        }
    }
}
