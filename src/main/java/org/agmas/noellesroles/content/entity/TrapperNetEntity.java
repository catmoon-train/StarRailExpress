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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 设陷者大招「捕网」实体：捕网枪右键发射的投掷物，客户端渲染为蜘蛛网方块。
 *
 * <p>命中玩家或落地后展开：半径 {@link #ROOT_RADIUS} 格内的玩家（发射者与杀手阵营除外）
 * 被禁锢 {@link #ROOT_DURATION} tick——无法移动、无法使用物品、无法使用技能。
 * 展开后的网留在原地作视觉提示，禁锢时间结束后消散。
 */
public class TrapperNetEntity extends ThrowableProjectile {

    /** 是否已落地展开（同步给客户端渲染用）。 */
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(
            TrapperNetEntity.class, EntityDataSerializers.BOOLEAN);

    /** 禁锢范围半径（格）。 */
    public static final double ROOT_RADIUS = 5.0;
    /** 禁锢时长（8s）。 */
    public static final int ROOT_DURATION = 8 * 20;
    /** 飞行超时（tick），防止射向虚空后永不落地。 */
    private static final int MAX_FLIGHT_TICKS = 10 * 20;

    private int landedTicks = 0;

    public TrapperNetEntity(EntityType<? extends TrapperNetEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LANDED, false);
    }

    public boolean isLanded() {
        return this.entityData.get(LANDED);
    }

    @Override
    public void tick() {
        if (isLanded()) {
            // 展开状态：静止停留，禁锢时间结束后消散
            if (!level().isClientSide() && ++landedTicks >= ROOT_DURATION) {
                discard();
            }
            return;
        }
        super.tick();
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), 0, 0.01, 0);
        } else if (tickCount > MAX_FLIGHT_TICKS) {
            discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide() || isLanded()) {
            return;
        }
        // 命中玩家：在其脚下展开
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player hit) {
            deploy(hit.position());
        } else {
            deploy(result.getLocation());
        }
    }

    /** 展开捕网：半径内玩家禁锢 8s（无法移动/使用物品/使用技能）。 */
    private void deploy(Vec3 pos) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        this.noPhysics = true;
        this.entityData.set(LANDED, true);

        // 音效与粒子：网落地展开
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.5f, 0.5f);
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.SPIDER_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.6f);
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
                pos.x, pos.y + 0.5, pos.z, 80, ROOT_RADIUS * 0.5, 0.5, ROOT_RADIUS * 0.5, 0.1);
        serverLevel.sendParticles(ParticleTypes.CLOUD,
                pos.x, pos.y + 0.3, pos.z, 30, ROOT_RADIUS * 0.4, 0.3, ROOT_RADIUS * 0.4, 0.05);

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverLevel);
        Entity owner = getOwner();
        int rooted = 0;
        for (Player victim : serverLevel.players()) {
            if (victim == owner || !GameUtils.isPlayerAliveAndSurvival(victim)) {
                continue;
            }
            if (gameWorld.isKillerTeam(victim)) {
                continue;
            }
            if (victim.position().distanceToSqr(pos) > ROOT_RADIUS * ROOT_RADIUS) {
                continue;
            }
            victim.stopRiding();
            victim.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ROOT_DURATION, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.USED_BANED, ROOT_DURATION, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, ROOT_DURATION, 0, false, false, true));
            if (victim instanceof ServerPlayer serverVictim) {
                serverVictim.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.net_rooted")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true);
            }
            rooted++;
        }
        if (owner instanceof ServerPlayer serverOwner) {
            serverOwner.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.net_deployed", rooted)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
