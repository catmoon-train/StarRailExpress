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

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 傀儡本体实体
 * 
 * 当傀儡师使用假人技能时，本体会在原位置生成一个本体实体。
 * 这个实体使用玩家的模型和皮肤，可以被攻击。
 * 如果本体被杀死，傀儡师也会死亡。
 */
public class PuppeteerBodyEntity extends LivingEntity {

    @Override
    public void kill() {
        this.discard();
    }

    /** 所有者 UUID */
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            PuppeteerBodyEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** 克隆替身与普通傀儡共用实体类型，但不继承伤害转移和十分钟寿命。 */
    private static final EntityDataAccessor<Boolean> CLONE_SUBSTITUTE = SynchedEntityData.defineId(
            PuppeteerBodyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Direction> SUBSTITUTE_DIRECTION = SynchedEntityData.defineId(
            PuppeteerBodyEntity.class, EntityDataSerializers.DIRECTION);
    // 不会被自然刷新
    private boolean persistenceRequired = false;

    /** 皮肤 GameProfile（用于渲染玩家皮肤） */
    private GameProfile skinProfile = null;

    /** 所有者玩家名称 */
    private String ownerName = "";

    /** 最大存活时间（10分钟 = 12000 tick），防止无限存在 */
    public static final int MAX_LIFETIME = 12000;

    /** 存活时间计数器 */
    private int lifetime = 0;

    /** 所有者玩家引用（缓存） */
    private Player ownerCache = null;
    /** 替身固定在制作时的坐标，避免水流等物理影响使其离开记录位置。 */
    private Vec3 substitutePosition;

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    /** 是否压制自定义名显示（傀儡师玩法默认压制；假人等子类可覆盖恢复）。 */
    protected boolean suppressCustomName() {
        return true;
    }

    @Override
    public boolean hasCustomName() {
        return !suppressCustomName() && super.hasCustomName();
    }

    @Override
    public void setCustomName(@Nullable Component component) {
        if (!suppressCustomName()) {
            super.setCustomName(component);
        }
    }

    @Override
    public boolean isCustomNameVisible() {
        return !suppressCustomName() && super.isCustomNameVisible();
    }

    @Override
    public boolean shouldShowName() {
        return !suppressCustomName() && super.shouldShowName();
    }

    public PuppeteerBodyEntity(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
        this.setNoGravity(false); // 有重力
        this.setCustomNameVisible(false);
        this.setHealth(20.0F); // 20点生命值（和玩家一样）
        this.persistenceRequired = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(CLONE_SUBSTITUTE, false);
        builder.define(SUBSTITUTE_DIRECTION, Direction.SOUTH);
    }

    /**
     * 设置所有者
     */
    public void setOwner(Player owner) {
        if (owner != null) {
            this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
            this.ownerCache = owner;
            this.ownerName = owner.getName().getString();

            // 设置皮肤（获取玩家的 GameProfile）
            if (owner instanceof ServerPlayer serverPlayer) {
                this.skinProfile = serverPlayer.getGameProfile();
            }

            // 设置自定义名称
            this.setCustomName(Component.translatable("entity.manipulator_body.name", owner.getName()));
            this.setCustomNameVisible(false);
            this.setPose(owner.getPose());
        }
    }

    /** 启用独立替身模式；所有者仅用于目标存活判定和皮肤，不是制造者。 */
    public void configureCloneSubstitute(Direction direction) {
        this.entityData.set(CLONE_SUBSTITUTE, true);
        this.entityData.set(SUBSTITUTE_DIRECTION, direction);
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.substitutePosition = position();
        // 只设置睡姿，不占用真实床，也不会清掉其他玩家的床占用。
        this.setPose(Pose.SLEEPING);
        this.setBoundingBox(this.makeBoundingBox());
    }

    /** 是否为克隆博士的独立替身。 */
    public boolean isCloneSubstitute() {
        return this.entityData != null && this.entityData.get(CLONE_SUBSTITUTE);
    }

    @Override
    public Direction getBedOrientation() {
        return isCloneSubstitute() ? this.entityData.get(SUBSTITUTE_DIRECTION) : super.getBedOrientation();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if ((CLONE_SUBSTITUTE.equals(accessor) || SUBSTITUTE_DIRECTION.equals(accessor)) && isCloneSubstitute()) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        if (!isCloneSubstitute()) {
            return super.makeBoundingBox();
        }
        Direction direction = getBedOrientation();
        double x = getX() - direction.getStepX() * 0.8D;
        double z = getZ() - direction.getStepZ() * 0.8D;
        double halfX = direction.getAxis() == Direction.Axis.X ? 1.0D : 0.3D;
        double halfZ = direction.getAxis() == Direction.Axis.Z ? 1.0D : 0.3D;
        return new AABB(x - halfX, getY(), z - halfZ, x + halfX, getY() + 0.4D, z + halfZ);
    }

    @Override
    public boolean shouldBeSaved() {
        // 替身只属于当前对局；区块卸载后不能留下无保护记录的持久假人。
        return !isCloneSubstitute() && super.shouldBeSaved();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (isCloneSubstitute() && !level().isClientSide() && isSleeping()) {
            stopSleeping();
        }
        super.remove(reason);
    }

    /**
     * 获取所有者 UUID
     */
    public Optional<UUID> getOwnerUuid() {
        return this.entityData.get(OWNER_UUID);
    }

    /**
     * 获取所有者玩家
     */
    public Player getOwner() {
        if (ownerCache != null) {
            return ownerCache;
        }
        Optional<UUID> ownerUuid = getOwnerUuid();
        if (ownerUuid.isPresent()) {
            ownerCache = level().getPlayerByUUID(ownerUuid.get());
            return ownerCache;
        }
        return null;
    }

    /**
     * 获取皮肤 GameProfile（用于客户端渲染）
     */
    public GameProfile getSkinProfile() {
        return skinProfile;
    }

    /** 直接设置皮肤 GameProfile（假人系统等无所有者玩家的场景使用）。 */
    public void setSkinProfile(GameProfile skinProfile) {
        this.skinProfile = skinProfile;
    }

    /**
     * 获取所有者名称
     */
    public String getOwnerName() {
        return ownerName;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide())
            return;
        if (isCloneSubstitute()) {
            Player target = getOwner();
            if (!SREGameWorldComponent.KEY.get(level()).isRunning()
                    || !(target instanceof ServerPlayer serverTarget) || serverTarget.hasDisconnected()
                    || target.level() != level() || !target.isAlive()
                    || !GameUtils.isPlayerAliveAndSurvival(target)) {
                discard();
                return;
            }
            if (substitutePosition != null) {
                this.setPos(substitutePosition);
            }
            this.setDeltaMovement(Vec3.ZERO);
            this.setPose(Pose.SLEEPING);
            this.setBoundingBox(this.makeBoundingBox());
            return;
        }
        if (this.persistenceRequired) {
            return;
        }
        final var gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent != null) {
            if (!gameWorldComponent.isRunning()) {
                discard();
            }
        }
        // 增加存活时间
        lifetime++;
        if (lifetime > MAX_LIFETIME) {
            this.discard();
            return;
        }

        // 检查所有者是否还存在
        Player owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            this.discard();
            return;
        }
    }

    public boolean playerHurt(Player player, ResourceLocation deathReason) {
        if (level().isClientSide())
            return false;
        if (org.agmas.noellesroles.game.roles.killer.clone_doctor.CloneDoctorSessionManager.blocksDamage(player))
            return false;
        if (isCloneSubstitute()) {
            discard();
            return true;
        }
        Player owner = getOwner();
        if (owner != null) {
            // 通知傀儡师组件本体死亡
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
            if (gameWorld.isRole(owner, ModRoles.PUPPETEER)) {
                PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(owner);
                puppeteerComp.onBodyDeath(player, deathReason);
            } else if (gameWorld.isRole(owner, ModRoles.RAVEN)) {
                var ravenData = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                        org.agmas.noellesroles.role_data.neutral.RavenRoleData.class, owner);
                if (ravenData != null) {
                    ravenData.onBodyDeath(player, deathReason);
                }
            } else {
                owner.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                // ModEffects.pierceDeath = true;
                // GameUtils.killPlayer(owner, true, player, deathReason);
                // ModEffects.pierceDeath = false;
            }
            discard();
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide())
            return false;
        if (isCloneSubstitute()) {
            if (amount <= 0 || isInvulnerableTo(source) || source.is(DamageTypes.IN_WALL)) {
                return false;
            }
            discard();
            return true;
        }

        if (source.is(DamageTypes.IN_WALL))
            return false;
        if (source.is(DamageTypes.PLAYER_ATTACK))
            return false;
        if (source.is(DamageTypes.DROWN))
            return false;
        if (source.is(DamageTypes.FREEZE))
            return false;
        if (source.is(DamageTypes.CACTUS))
            return false;
        // 调用父类处理伤害
        boolean result = super.hurt(source, amount);

        // 如果死亡，通知傀儡师
        if (this.isDeadOrDying()) {
            Player owner = getOwner();
            if (owner != null) {
                // 通知傀儡师组件本体死亡
                SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
                if (gameWorld.isRole(owner, ModRoles.PUPPETEER)) {
                    ModComponents.PUPPETEER.get(owner).onBodyDeath();
                } else if (gameWorld.isRole(owner, ModRoles.RAVEN)) {
                    var ravenData = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                            org.agmas.noellesroles.role_data.neutral.RavenRoleData.class, owner);
                    if (ravenData != null) {
                        ravenData.onBodyDeath(null, Noellesroles.id("raven_body_death"));
                    }
                } else {
                    owner.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                    // ModEffects.pierceDeath = true;
                    // Player killer = null;
                    // if (source.getEntity() instanceof Player k) {
                    // killer = k;
                    // }
                    // GameUtils.killPlayer(owner, true, killer,
                    // GameConstants.DeathReasons.GENERAL_ATTACK);
                    // ModEffects.pierceDeath = false;
                    // discard();
                }
                discard();
            }
        }

        return result;
    }

    @Override
    public void die(DamageSource damageSource) {
        if (isCloneSubstitute()) {
            discard();
            return;
        }
        super.die(damageSource);

        // 确保通知傀儡师
        Player owner = getOwner();
        if (owner != null) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level());
            if (gameWorld.isRole(owner, ModRoles.PUPPETEER)) {
                ModComponents.PUPPETEER.get(owner).onBodyDeath();
            } else if (gameWorld.isRole(owner, ModRoles.RAVEN)) {
                var ravenData = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                        org.agmas.noellesroles.role_data.neutral.RavenRoleData.class, owner);
                if (ravenData != null) {
                    ravenData.onBodyDeath(null, Noellesroles.id("raven_body_death"));
                }
            }
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("PersistenceRequired")) {
            this.persistenceRequired = nbt.getBoolean("PersistenceRequired");
        }
        if (nbt.contains("OwnerUUID")) {
            this.entityData.set(OWNER_UUID, Optional.of(nbt.getUUID("OwnerUUID")));
        }
        if (nbt.contains("OwnerName")) {
            this.ownerName = nbt.getString("OwnerName");
        }
        // SkinProfile 通过 OwnerUUID 在客户端动态获取，不需要从 NBT 加载
        this.lifetime = nbt.contains("Lifetime") ? nbt.getInt("Lifetime") : 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("PersistenceRequired", this.persistenceRequired);

        Optional<UUID> ownerUuid = getOwnerUuid();
        ownerUuid.ifPresent(uuid -> nbt.putUUID("OwnerUUID", uuid));
        nbt.putString("OwnerName", this.ownerName);
        // SkinProfile 通过 OwnerUUID 在客户端动态获取，不需要保存到 NBT
        nbt.putInt("Lifetime", this.lifetime);
    }

    @Override
    public boolean isPickable() {
        return true; // 可以被击中
    }

    @Override
    public boolean isPushable() {
        return false; // 不能被推动
    }

    @Override
    public boolean isAttackable() {
        return true; // 可以被攻击
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true; // 可以被远程武器击中（手枪、弓箭等）
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // 免疫淹死伤害（渡鸦/傀儡师的玩家傀儡不应被淹死）
        if (source.is(DamageTypes.DROWN)) {
            return true;
        }
        // 虚空伤害不免疫，让实体正常死亡
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            return false;
        }
        // 对其他所有伤害都不免疫
        return false;
    }

    @Override
    public Iterable<net.minecraft.world.item.ItemStack> getArmorSlots() {
        return java.util.Collections.emptyList();
    }

    @Override
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        // 不装备任何物品
    }

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }
}
