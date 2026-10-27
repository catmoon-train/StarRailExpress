/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role_data.innocence.StarGathererRoleData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 摘星者「星之权杖」/「星之引力」召唤的追踪星星。
 * <p>
 * 星星升到目标头顶后俯冲追击目标，命中即用死因 {@link GameConstants.DeathReasons#STAR}（"星星"）击杀，
 * 撞方块或寿命耗尽即消散。只有摘星者自己能看到这颗星星（客户端渲染器按召唤者 UUID 过滤），
 * 但粒子与音效是服务端广播的，受害者能看到星星砸下来的光。
 */
public class StarProjectileEntity extends Entity {

    /** 追星目标。 */
    @Nullable
    private UUID targetId;
    /** 召唤者（摘星者本人，用于把击杀记到他头上、并做客户端可见性过滤）。 */
    @Nullable
    private UUID ownerId;

    /** 升空阶段：先升到目标头顶再俯冲。 */
    private int ascendTicks = 10;
    /** 剩余寿命（tick）。 */
    private int life = 120;
    /** 是否已经命中 / 撞毁，避免重复结算。 */
    private boolean spent;

    public StarProjectileEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * 生成一颗追踪星星。
     *
     * @param from   起点（通常在高空）
     * @param target 追踪目标
     * @param owner  召唤者，可为 null
     */
    public static StarProjectileEntity spawn(ServerLevel level, Vec3 from, @Nullable ServerPlayer target,
            @Nullable ServerPlayer owner) {
        StarProjectileEntity star = new StarProjectileEntity(ModEntities.STAR, level);
        star.setPos(from.x, from.y, from.z);
        star.targetId = target == null ? null : target.getUUID();
        star.ownerId = owner == null ? null : owner.getUUID();
        level.addFreshEntity(star);
        level.playSound(null, star.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.2F, 1.8F);
        return star;
    }

    @Nullable
    public UUID getTargetId() {
        return this.targetId;
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Life", this.life);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.spent) {
            return;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }

        // 纯粹的拖尾粒子（每 tick 一包，数量很小）
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(),
                3, 0.15D, 0.15D, 0.15D, 0.0D);

        if (--this.life <= 0) {
            vanish(level);
            return;
        }

        ServerPlayer target = this.targetId == null ? null
                : level.getServer().getPlayerList().getPlayer(this.targetId);

        // 目标死亡 / 掉线 → 原地消散
        if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
            vanish(level);
            return;
        }

        Vec3 pos = this.position();
        Vec3 goal;
        if (this.ascendTicks > 0) {
            // 先垂直升到目标头顶 4 格处
            this.ascendTicks--;
            goal = new Vec3(target.getX(), target.getY() + 4.0D, target.getZ());
        } else {
            // 俯冲：直接追向目标胸口
            goal = new Vec3(target.getX(), target.getY() + 0.8D, target.getZ());
        }

        Vec3 delta = goal.subtract(pos);
        double distance = delta.length();
        if (distance > 0.0001D) {
            double speed = this.ascendTicks > 0 ? 0.6D : 1.4D;
            Vec3 step = delta.normalize().scale(Math.min(speed, distance));
            this.setPos(pos.x + step.x, pos.y + step.y, pos.z + step.z);
        }
        this.setDeltaMovement(Vec3.ZERO);

        // 命中判定
        AABB hitBox = this.getBoundingBox().inflate(0.85D);
        List<ServerPlayer> hits = level.getEntitiesOfClass(ServerPlayer.class, hitBox,
                p -> GameUtils.isPlayerAliveAndSurvival(p) && !p.isSpectator());
        if (!hits.isEmpty()) {
            ServerPlayer killer = this.ownerId == null ? null
                    : level.getServer().getPlayerList().getPlayer(this.ownerId);
            boolean hitAny = false;
            for (ServerPlayer victim : hits) {
                if (killer == victim) {
                    continue;
                }
                hitAny = true;
                GameUtils.forceKillPlayer(victim, true, killer, GameConstants.DeathReasons.STAR);
            }
            // 权杖只要命中玩家（不论是否真的击杀）→ 召唤者进入 20 秒原版物品冷却
            if (hitAny && killer != null) {
                killer.getCooldowns().addCooldown(ModItems.STAR_SCEPTER,
                        StarGathererRoleData.SCEPTER_HIT_COOLDOWN_TICKS);
            }
            this.spent = true;
            implode(level);
            return;
        }

        // 撞到方块 → 消散
        if (this.ascendTicks <= 0 && !level.noCollision(this, this.getBoundingBox().deflate(0.05D))) {
            this.spent = true;
            implode(level);
        }
    }

    private void implode(ServerLevel level) {
        level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(),
                40, 0.5D, 0.5D, 0.5D, 0.15D);
        level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundSource.PLAYERS, 1.5F, 1.2F);
        this.discard();
    }

    private void vanish(ServerLevel level) {
        this.spent = true;
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(),
                12, 0.3D, 0.3D, 0.3D, 0.05D);
        this.discard();
    }
}
