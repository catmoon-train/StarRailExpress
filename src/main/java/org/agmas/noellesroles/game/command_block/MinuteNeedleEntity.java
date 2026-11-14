package org.agmas.noellesroles.game.command_block;

import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 分针专用抛射物，复用项目的投掷基类与物品渲染，不改共享命令方块投掷物。 */
public class MinuteNeedleEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {
    private static final int LIFETIME = 5 * 20;

    public MinuteNeedleEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type, Level level) {
        super(type, level);
    }

    public static boolean throwFrom(ServerPlayer owner, ItemStack stack) {
        MinuteNeedleEntity needle = new MinuteNeedleEntity(ModEntities.MINUTE_NEEDLE, owner.level());
        needle.setOwner(owner);
        needle.setPos(owner.getX(), owner.getEyeY() - 0.1D, owner.getZ());
        needle.setItem(stack.copyWithCount(1));
        needle.shootFromRotation(owner, owner.getXRot(), owner.getYRot(), 0.0F, 1.8F, 0.0F);
        if (!owner.level().addFreshEntity(needle)) {
            return false;
        }
        owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 0.8F, 1.3F);
        return true;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.MINUTE_NEEDLE;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && entity instanceof ServerPlayer target
                && GameUtils.isPlayerAliveAndSurvival(target) && super.canHitEntity(entity);
    }

    @Override
    protected void onHit(HitResult hit) {
        if (level().isClientSide) {
            return;
        }
        // 不调用原版伤害结算，只在真实碰撞后交给钟摆规则处理一次。
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof ServerPlayer target
                && getOwner() instanceof ServerPlayer owner) {
            PendulumRole.onMinuteHit(owner, target);
        }
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (tickCount >= LIFETIME
                || !(getOwner() instanceof ServerPlayer owner) || !PendulumRole.canUse(owner))) {
            discard();
        }
    }
}
