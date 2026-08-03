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
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设陷者「泥沼」陷阱实体（重做版）。
 *
 * <p>放在地面（仅设陷者本人可见）。被踩中后触发：以陷阱为中心半径 3 格内的所有
 * 玩家（所有者与杀手阵营除外）陷入泥沼——5s 内无法移动、无法使用物品。
 * 一次性陷阱，触发后消失。放置消耗 45 金币（在组件中扣除）。
 */
public class MudTrapEntity extends Entity {

    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            MudTrapEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 踩中触发半径（格）。 */
    public static final double TRIGGER_RADIUS = 1.0;
    /** 触发后的困陷范围半径（格）。 */
    public static final double ROOT_RADIUS = 3.0;
    /** 困陷时长（5s）。 */
    public static final int ROOT_DURATION = 6 * 20;

    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;

    public MudTrapEntity(EntityType<?> type, Level world) {
        super(type, world);
        this.setInvisible(true); // 对所有人隐形（渲染器只对所有者绘制）
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER_UUID, Optional.empty());
    }

    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
        }
    }

    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    /** 只有设陷者本人可以看到自己的泥沼。 */
    public boolean isVisibleTo(Player player) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        return ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get());
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        return !isVisibleTo(player);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        Player owner = getOwner();
        if (owner == null) {
            this.discard();
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (!gameWorld.isRole(owner, ModRoles.TRAPPER)) {
            this.discard();
            return;
        }

        try {
            checkTrigger();
        } catch (java.util.ConcurrentModificationException e) {
            // 实体列表在 ticking 期间被并发修改时跳过本帧检测（MC-188315）
        }
    }

    private void checkTrigger() {
        Level world = level();
        Vec3 pos = this.position();
        AABB detectionBox = new AABB(
                pos.x - TRIGGER_RADIUS, pos.y - 0.5, pos.z - TRIGGER_RADIUS,
                pos.x + TRIGGER_RADIUS, pos.y + 2.0, pos.z + TRIGGER_RADIUS);
        List<Player> players = world.getEntitiesOfClass(Player.class, detectionBox, this::canTrigger);
        if (!players.isEmpty()) {
            triggerTrap();
        }
    }

    private boolean canTrigger(Player player) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent() && player.getUUID().equals(ownerUuid.get())) {
            return false;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
        if (gameWorld.isKillerTeam(player)) {
            return false;
        }
        // 被操纵师操控的玩家：弹回且不触发陷阱
        return !org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA.bounceBackIfControlled(player);
    }

    /** 触发：半径 3 格内所有非杀手阵营玩家陷入泥沼（禁止移动/使用物品 5s）。 */
    private void triggerTrap() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player owner = getOwner();
        Vec3 pos = position();

        // 音效与粒子：泥浆飞溅
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.MUD_BREAK, SoundSource.PLAYERS, 1.5f, 0.7f);
        serverLevel.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.2f, 0.6f);
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()),
                pos.x, pos.y + 0.2, pos.z, 60, ROOT_RADIUS * 0.6, 0.3, ROOT_RADIUS * 0.6, 0.1);

        int rooted = 0;
        for (Player victim : serverLevel.players()) {
            if (victim.position().distanceToSqr(pos) > ROOT_RADIUS * ROOT_RADIUS) {
                continue;
            }
            if (!canTrigger(victim)) {
                continue;
            }
            victim.stopRiding();
            victim.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, ROOT_DURATION, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.USED_BANED, ROOT_DURATION, 0, false, false, true));
            victim.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, ROOT_DURATION, 0, false, false, true));
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MUD.defaultBlockState()),
                    victim.getX(), victim.getY() + 0.3, victim.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
            if (victim instanceof ServerPlayer serverVictim) {
                serverVictim.displayClientMessage(
                        Component.translatable("message.noellesroles.trapper.mud_triggered_victim")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        true);
            }
            rooted++;
        }

        if (owner instanceof ServerPlayer serverOwner) {
            serverOwner.displayClientMessage(
                    Component.translatable("message.noellesroles.trapper.mud_triggered_notify", rooted)
                            .withStyle(ChatFormatting.GREEN),
                    true);
        }

        // 一次性陷阱
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag nbt) {
        if (nbt.contains("OwnerUUID")) {
            try {
                UUID uuid = UUID.fromString(nbt.getString("OwnerUUID"));
                this.entityData.set(OWNER_UUID, Optional.of(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag nbt) {
        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putString("OwnerUUID", uuid.toString()));
    }
}
