package org.agmas.noellesroles.game.command_block.entity;

import java.util.HashSet;

import org.agmas.noellesroles.game.command_block.StringerRole;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.killer.StringerRoleData;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.ParticleFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** 专用丝线团，不经过 CommandBlockThrownEntity 或伤害型手雷的击杀流程。 */
public class ThreadBallEntity extends ThrowableItemProjectile {
    public static final float RADIUS = 4F;
    public static final int HOLD_TICKS = 2 * 20;
    public static final int MARK_TICKS = 15 * 20;
    private StringerRoleData source;

    public ThreadBallEntity(EntityType<? extends ThreadBallEntity> type, Level level) {
        super(type, level);
    }

    public void setSource(StringerRoleData source) {
        this.source = source;
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.THREAD_BALL;
    }

    private boolean validSource() {
        return source != null && getOwner() instanceof ServerPlayer owner && owner.level() == level()
                && StringerRole.canAct(owner) && RoleData.getNullable(StringerRoleData.class, owner) == source;
    }

    @Override
    public void tick() {
        if (!level().isClientSide && (!validSource() || tickCount >= 200)) {
            discard();
            return;
        }
        super.tick();
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel world) || isRemoved()) return;
        if (validSource()) {
            // 普通 GrenadeEntity 无倒计时引信，碰撞即引爆；复用其 4 格、多高度遮挡采样。
            var affected = new HashSet<Entity>();
            for (double dy : new double[] { 0, 0.5D, -0.5D, 1D, -1D }) {
                affected.addAll(GrenadeEntity.getPlayersAffectedByExplosion(world, getX(), getY() + dy, getZ(), RADIUS));
            }
            for (Entity entity : affected) {
                if (entity instanceof ServerPlayer target) {
                    source.mark(target, MARK_TICKS);
                    source.hold(target, HOLD_TICKS);
                }
            }
            world.playSound(null, blockPosition(), TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 1.5F, 1.3F);
            ParticleFx.burst(world, ParticleTypes.CLOUD, getX(), getY(), getZ(), 40, 1.5D, 0.5D, 1.5D, 0.03D);
        }
        discard();
    }
}
