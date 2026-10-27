package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import org.joml.Vector3f;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

/**
 * 毛线球抛射物（参考番茄抛射物）。
 *
 * <p>命中存活玩家时给予 8 秒「腿瘸」；命中其他物体则把毛线球掉落在原地（可拾回）。
 * 不能命中投掷者本人。</p>
 */
public class YarnBallEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 命中玩家给予的「腿瘸」时长（8 秒）。 */
    public static final int LIMP_TICKS = 160;

    public YarnBallEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            Level world) {
        super(entityType, world);
    }

    public YarnBallEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> entityType,
            LivingEntity owner, Level world) {
        super(entityType, owner, world);
        setItem(new ItemStack(ModItems.YARN_BALL));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.YARN_BALL;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity == getOwner()) {
            return false;
        }
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof ServerPlayer target
                && GameUtils.isPlayerAliveAndSurvival(target)) {
            target.addEffect(new MobEffectInstance(ModEffects.LIMP, LIMP_TICKS, 0, false, true, true));
            target.displayClientMessage(
                    Component.translatable("message.noellesroles.tailor.yarn_hit").withStyle(ChatFormatting.RED),
                    true);
            serverLevel.sendParticles(new DustParticleOptions(new Vector3f(0.96F, 0.90F, 0.92F), 1.2F),
                    target.getX(), target.getY() + 1.0D, target.getZ(),
                    10, 0.35D, 0.5D, 0.35D, 0.02D);
        } else {
            // 掷空：掉落在原地供拾回
            ItemEntity drop = new ItemEntity(serverLevel, getX(), getY(), getZ(), new ItemStack(ModItems.YARN_BALL));
            drop.setPickUpDelay(10);
            drop.setDeltaMovement(getDeltaMovement().scale(0.1D));
            if (getOwner() != null) {
                drop.setThrower(getOwner());
            }
            serverLevel.addFreshEntity(drop);
        }
        discard();
    }
}
