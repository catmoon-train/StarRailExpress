package org.agmas.noellesroles.content.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;

/**
 * 照明弹实体 - 投掷后飞行，撞到方块时放置照明弹方块，10秒后自动消失
 */
public class FlareEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFETIME = 200; // 10秒

    public FlareEntity(EntityType<? extends ThrowableItemProjectile> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.FLARE;
    }

    @Override
    public void tick() {
        super.tick();
        // 客户端粒子
        if (this.level().isClientSide && this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX() + random.nextGaussian() * 0.05,
                    this.getY() + random.nextGaussian() * 0.05,
                    this.getZ() + random.nextGaussian() * 0.05,
                    0, 0.01, 0);
        }
        // 10秒超时消失
        if (!this.level().isClientSide && this.tickCount > MAX_LIFETIME) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            BlockPos placePos;
            if (hitResult instanceof BlockHitResult blockHit) {
                placePos = blockHit.getBlockPos().relative(blockHit.getDirection());
            } else {
                placePos = BlockPos.containing(hitResult.getLocation());
            }

            BlockState flareState = ModBlocks.FLARE_BLOCK.defaultBlockState();
            if (this.level().getBlockState(placePos).isAir()) {
                this.level().setBlock(placePos, flareState, 3);
                serverLevel.playSound(null, placePos, SoundEvents.FIRECHARGE_USE,
                        SoundSource.BLOCKS, 1.0f, 1.2f);
            } else {
                // 如果目标位置不是空气，尝试放在碰撞面的相邻位置
                BlockPos altPos = BlockPos.containing(hitResult.getLocation());
                if (this.level().getBlockState(altPos).isAir()) {
                    this.level().setBlock(altPos, flareState, 3);
                    serverLevel.playSound(null, altPos, SoundEvents.FIRECHARGE_USE,
                            SoundSource.BLOCKS, 1.0f, 1.2f);
                }
            }
            this.discard();
        }
    }
}
