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

import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import io.wifi.starrailexpress.api.data.RoleData;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.halloween.HalloweenCombat;
import org.agmas.noellesroles.game.halloween.HalloweenConfig;
import org.agmas.noellesroles.game.night.NightEvent;
import org.agmas.noellesroles.game.night.NightUndeadRules;
import org.agmas.noellesroles.game.night.NightUndeadRules.Source;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * 亡灵实体：领主召唤保留远程追击与感染；活动及无主亡灵只在出生区域近距警戒。
 *
 * <p>
 * 特性：
 * <ul>
 * <li>使用死者生前的皮肤外观，周身环绕淡紫色雾气。</li>
 * <li>领主亡灵发现 40 格内活人后穿墙追击；非领主亡灵只攻击 12 格内玩家，平时在出生点 8 格内游荡。</li>
 * <li>领主亡灵每 1.5 秒注入感染并结算配置伤害；非领主亡灵沿用活动简单近战。</li>
 * <li>持续 90 秒后化为紫色烟雾消散；剩余 30 秒减速、剩余 10 秒闪烁示警。</li>
 * <li>无实体碰撞（穿墙、无重力悬浮），无法开门、无法使用道具。</li>
 * </ul>
 */
public class UndeadEntity extends PathfinderMob {

    /** 皮肤所属玩家 UUID（死者），仅用于客户端渲染外观。 */
    private static final EntityDataAccessor<Optional<UUID>> SKIN_UUID = SynchedEntityData.defineId(
            UndeadEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 是否进入消散预警（剩余 10 秒），客户端用于闪烁渲染。 */
    private static final EntityDataAccessor<Boolean> FLICKERING = SynchedEntityData.defineId(
            UndeadEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int DEFAULT_LIFETIME = 90 * 20;
    private static final int WARNING_FADE_TICKS = 30 * 20;
    private static final int WARNING_FLICKER_TICKS = 10 * 20;
    private static final double PERCEPTION_RANGE = 40.0D;
    private static final int ATTACK_INTERVAL = 30; // 1.5 秒
    private static final double TOUCH_RANGE = 1.6D;
    /** 亡灵每次攻击注入的感染值（在原 10.35 基础上提高 60%）。 */
    private static final float INFECTION_PER_HIT = 16.56f;
    private static final double BASE_SPEED = 0.22D;
    /** 幽灵直线穿墙时的移动速度系数（相对 MOVEMENT_SPEED，避免无视墙壁后追击过强）。 */
    private static final double GHOST_SPEED_FACTOR = 0.55;
    /** 重新选择目标的间隔（节流处理）。 */
    private static final int RETARGET_INTERVAL = 10;

    /** 召唤者（亡灵之主）UUID，用于结算感染与统计存活数量。 */
    private UUID ownerUuid = null;
    private Player ownerCache = null;

    private int remainingLifetime = DEFAULT_LIFETIME;
    private int attackCooldown = 0;
    private boolean slowedNearEnd = false;
    /** 当前追踪目标 UUID；周期性重新评估（优先可抵达目标）。 */
    private UUID currentTargetUuid = null;
    private int retargetCooldown = 0;

    /** 明确区分来源，不能通过 owner 是否非空判断（万圣节亡灵也可能有主人）。 */
    private Source source = Source.WILD;
    /** 首次服务端 tick 捕获实际出生位置，兼容先 setup 后 moveTo 的生成顺序。 */
    private Vec3 spawnAnchor;
    private Vec3 wanderTarget;
    private int wanderTicks;
    private boolean returningToAnchor;
    private UUID halloweenTarget = null;
    private int halloweenTargetTicks = 0;

    public UndeadEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setHealth(this.getMaxHealth());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FOLLOW_RANGE, PERCEPTION_RANGE)
                .add(Attributes.MOVEMENT_SPEED, BASE_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        // 仅保留徘徊与张望；追击与感染逻辑在 tick() 中手动处理。
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D) {
            @Override
            public boolean canUse() { return isLordSummoned() && super.canUse(); }
            @Override
            public boolean canContinueToUse() { return isLordSummoned() && super.canContinueToUse(); }
        });
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_UUID, Optional.empty());
        builder.define(FLICKERING, false);
    }

    /** 初始化亡灵：设置召唤者、皮肤来源与持续时间。 */
    public void setup(Player owner, UUID skinPlayerUuid, int lifetimeTicks) {
        resetSource(Source.LORD);
        this.ownerUuid = owner.getUUID();
        this.ownerCache = owner;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skinPlayerUuid));
        this.remainingLifetime = lifetimeTicks;
        // this.addEffect(new MobEffectInstance(MobEffects.GLOWING, lifetimeTicks + 200,
        // 0, false, false, false));
    }

    /** 活动亡灵可无召唤者；召唤者死亡或离线不影响活动亡灵。 */
    public void setupHalloween(@Nullable Player owner, UUID skin, int lifetime, @Nullable UUID target) {
        resetSource(Source.HALLOWEEN);
        this.ownerUuid = owner == null ? null : owner.getUUID();
        this.ownerCache = owner;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skin));
        this.remainingLifetime = Math.max(1, lifetime);
        setHalloweenTarget(target, HalloweenConfig.get().gunShieldSuppressionTicks);
    }

    /** 黑夜事件入口：无领主归属，不依赖万圣节运行时、名额或感染。 */
    public void setupNight(UUID skin, int lifetime) {
        resetSource(Source.NIGHT);
        this.ownerUuid = null;
        this.ownerCache = null;
        this.entityData.set(SKIN_UUID, Optional.ofNullable(skin));
        this.remainingLifetime = Math.max(1, lifetime);
    }

    private void resetSource(Source source) {
        this.source = source;
        this.spawnAnchor = null;
        this.wanderTarget = null;
        this.returningToAnchor = false;
        setHalloweenTarget(null, 0);
    }

    public boolean isHalloween() { return source == Source.HALLOWEEN; }
    public boolean isNight() { return source == Source.NIGHT; }
    public boolean isLordSummoned() { return source == Source.LORD; }
    public boolean hasHalloweenTarget() { return halloweenTarget != null; }

    /** 惊魂枪的临时优先目标，允许包含召唤者；到期、死亡、跨维度后恢复原筛选。 */
    public void setHalloweenTarget(@Nullable UUID target, int ticks) {
        this.halloweenTarget = ticks > 0 ? target : null;
        this.halloweenTargetTicks = target == null ? 0 : Math.max(0, ticks);
        this.currentTargetUuid = null;
        this.retargetCooldown = 0;
        this.setTarget(null);
    }

    public UUID getSkinUuid() {
        return this.entityData.get(SKIN_UUID).orElse(null);
    }

    public boolean isFlickering() {
        return this.entityData.get(FLICKERING);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Player getOwner() {
        if (ownerCache != null && ownerCache.isAlive()) {
            return ownerCache;
        }
        if (ownerUuid != null) {
            ownerCache = level().getPlayerByUUID(ownerUuid);
        }
        return ownerCache;
    }

    /** 延长存活时间（亡灵延命药剂 / 时之沙漏调用）。 */
    public void addLifetime(int ticks) {
        this.remainingLifetime = NightUndeadRules.extendLifetime(this.remainingLifetime, ticks);
    }

    public void setLifetime(int ticks) {
        this.remainingLifetime = ticks;
    }

    public int getRemainingLifetime() {
        return remainingLifetime;
    }

    @Override
    public void tick() {
        // 活动结束/停局先于时间冻结处理，不能留下冻结中的黑夜亡灵。
        if (!level().isClientSide && isNight()) {
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level());
            if (game == null || !game.isRunning() || !NightEvent.isUndeadActive(this)) {
                discard();
                return;
            }
        }
        if (!level().isClientSide && isHalloween() && !HalloweenCombat.isEventUndeadActive(this)) {
            discard();
            return;
        }
        if (SREGameTimeComponent.KEY.get(level()).isTimeFrozen()) {
            if (!this.isNoAi()) {
                this.setNoAi(true);
            }
            return;
        } else {
            if (this.isNoAi()) {
                this.setNoAi(false);
            }
        }
        this.noPhysics = true;
        this.setNoGravity(true);
        if (!level().isClientSide) {
            if (spawnAnchor == null) spawnAnchor = position();
            if (!isLordSummoned()) {
                getNavigation().stop();
                setSpeed(0);
                setDeltaMovement(Vec3.ZERO);
            }
        }
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 幽灵特性：无实体碰撞（穿墙）+ 无重力悬浮，移动完全由下方逻辑手动驱动。
        this.noPhysics = true;
        this.setNoGravity(true);

        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(level());
        if (gameWorldComponent == null || !gameWorldComponent.isRunning()) {
            disappear(serverLevel, false);
            return;
        }

        // 召唤者掉线/死亡则消散
        Player owner = getOwner();
        if (isLordSummoned() && (owner == null || !owner.isAlive() || owner.isSpectator())) {
            disappear(serverLevel, false);
            return;
        }

        remainingLifetime--;
        if (remainingLifetime <= 0) {
            disappear(serverLevel, true);
            return;
        }

        // 剩余 30 秒：减速 10%
        if (!slowedNearEnd && remainingLifetime <= WARNING_FADE_TICKS) {
            slowedNearEnd = true;
            var attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) {
                attr.setBaseValue(BASE_SPEED * 0.9D);
            }
        }
        // 剩余 10 秒：闪烁 + 低沉音效
        boolean flicker = remainingLifetime <= WARNING_FLICKER_TICKS;
        if (flicker != this.entityData.get(FLICKERING)) {
            this.entityData.set(FLICKERING, flicker);
            if (flicker) {
                serverLevel.playSound(null, blockPosition(), SoundEvents.WARDEN_HEARTBEAT,
                        SoundSource.HOSTILE, 1.2f, 0.5f);
            }
        }

        spawnMistParticles(serverLevel);

        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (retargetCooldown > 0) {
            retargetCooldown--;
        }

        // 非领主丢失警戒范围后先回出生点，不被远处枪械临时指令继续牵走。
        if (!isLordSummoned() && currentTargetUuid != null
                && serverLevel.getPlayerByUUID(currentTargetUuid) instanceof ServerPlayer previous
                && !canEngage(previous)) {
            returnToAnchor();
        }
        if (returningToAnchor && position().distanceToSqr(spawnAnchor) <= 1.0) {
            returningToAnchor = false;
        }

        // 临时指令保留领主的远程号令；活动亡灵仍须通过相同的 12 格限制。
        ServerPlayer victim = null;
        if (halloweenTarget != null && halloweenTargetTicks > 0) {
            if (serverLevel.getPlayerByUUID(halloweenTarget) instanceof ServerPlayer forced
                    && GameUtils.isPlayerAliveAndSurvival(forced) && !forced.isSpectator()
                    && !forced.isCreative() && !forced.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME)
                    && canEngage(forced) && !returningToAnchor) {
                victim = forced;
                halloweenTargetTicks--;
            } else {
                if (!isLordSummoned()) returnToAnchor();
                else setHalloweenTarget(null, 0);
            }
        }
        if (victim == null && !returningToAnchor) {
            if (halloweenTarget != null) setHalloweenTarget(null, 0);
            victim = resolveCurrentTarget(serverLevel, gameWorldComponent);
            if (victim == null || retargetCooldown <= 0) {
                retargetCooldown = RETARGET_INTERVAL;
                ServerPlayer reselected = selectBestTarget(serverLevel, gameWorldComponent);
                if (reselected != null) {
                    victim = reselected;
                    currentTargetUuid = reselected.getUUID();
                } else if (victim == null) {
                    currentTargetUuid = null;
                }
            }
        }

        if (victim != null) {
            setTarget(victim);
            getLookControl().setLookAt(victim);
            // 幽灵穿墙：无视寻路，直线朝目标飞行。
            ghostChase(victim);

            if (attackCooldown <= 0 && this.distanceToSqr(victim) <= TOUCH_RANGE * TOUCH_RANGE) {
                performAttack(serverLevel, owner, victim);
            }
        } else {
            setTarget(null);
            if (!isLordSummoned()) ghostWander();
        }
    }

    /** 临时枪械目标与自然目标共用警戒距离，禁止绕过来源限制。 */
    private boolean canEngage(ServerPlayer victim) {
        return NightUndeadRules.canEngage(source, distanceToSqr(victim),
                position().distanceToSqr(spawnAnchor), victim.position().distanceToSqr(spawnAnchor));
    }

    private void returnToAnchor() {
        returningToAnchor = true;
        wanderTarget = null;
        setHalloweenTarget(null, 0);
    }

    /** 穿墙浮游：漫游目标始终在出生点球体内；超出后回锚点，绝不累加随机漂移。 */
    private void ghostWander() {
        if (returningToAnchor || !NightUndeadRules.isWithinRoam(position().distanceToSqr(spawnAnchor))) {
            wanderTarget = spawnAnchor;
        } else if (--wanderTicks <= 0 || wanderTarget == null || position().distanceToSqr(wanderTarget) < 0.25) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = random.nextDouble() * (NightUndeadRules.ROAM_RADIUS - 1);
            wanderTarget = spawnAnchor.add(Math.cos(angle) * radius, random.nextDouble() * 2 - 1,
                    Math.sin(angle) * radius);
            wanderTicks = 40 + random.nextInt(60);
        }
        ghostMove(wanderTarget.subtract(position()), 0.6);
    }

    /** 幽灵穿墙移动：朝目标中心直线推进，无视方块碰撞（noPhysics）。 */
    private void ghostChase(ServerPlayer victim) {
        getNavigation().stop();
        Vec3 selfCenter = this.position().add(0, this.getBbHeight() * 0.5, 0);
        Vec3 targetCenter = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
        ghostMove(targetCenter.subtract(selfCenter), 1.0);
    }

    private void ghostMove(Vec3 dir, double speedFactor) {
        double dist = dir.length();
        if (dist > 0.05) {
            var attr = this.getAttribute(Attributes.MOVEMENT_SPEED);
            double speed = (attr != null ? attr.getValue() : BASE_SPEED) * GHOST_SPEED_FACTOR * speedFactor;
            Vec3 step = dir.scale(Math.min(speed, dist) / dist);
            this.move(MoverType.SELF, step);
        }
        // 清空速度，避免 travel() 在无重力下叠加漂移。
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** 校验当前目标是否仍可作为攻击对象（存活、感知范围内）。 */
    private ServerPlayer resolveCurrentTarget(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        if (currentTargetUuid == null) {
            return null;
        }
        if (!(serverLevel.getPlayerByUUID(currentTargetUuid) instanceof ServerPlayer victim)) {
            return null;
        }
        if (!isValidVictim(victim, gameWorldComponent)) {
            return null;
        }
        if (!canEngage(victim) || this.distanceToSqr(victim) > PERCEPTION_RANGE * PERCEPTION_RANGE) {
            return null;
        }
        return victim;
    }

    /** 选择最佳目标：感知范围内最近的有效目标（穿墙后所有目标皆可抵达）。 */
    private ServerPlayer selectBestTarget(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent) {
        return serverLevel.players().stream()
                .filter(p -> isValidVictim(p, gameWorldComponent))
                .filter(this::canEngage)
                .filter(p -> this.distanceToSqr(p) <= PERCEPTION_RANGE * PERCEPTION_RANGE)
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    /** 目标筛选条件：存活、冒险模式、非杀手阵营、非召唤者。 */
    private boolean isValidVictim(ServerPlayer p, SREGameWorldComponent gameWorldComponent) {
        if (!isLordSummoned()) {
            return p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                    && GameUtils.isPlayerAliveAndSurvival(p)
                    && !p.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME);
        }
        return p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                && GameUtils.isPlayerAliveAndSurvival(p)
                && !p.getUUID().equals(ownerUuid)
                && !gameWorldComponent.isKillerTeam(p);
    }

    /** 执行一次攻击：挥手、注入感染并造成真实伤害（归属于亡灵之主）。 */
    private void performAttack(ServerLevel serverLevel, Player owner, ServerPlayer victim) {
        attackCooldown = ATTACK_INTERVAL;
        this.swing(net.minecraft.world.InteractionHand.MAIN_HAND);


        if (!isLordSummoned()) {
            // 独立活动亡灵只有简单近战，不注入亡灵之主感染值，也不借用其职业技能。
            if (!victim.hasEffect(org.agmas.noellesroles.init.ModEffects.SAFE_TIME)) {
                victim.hurt(this.damageSources().mobAttack(this), 2.0f);
            }
            serverLevel.playSound(null, victim.blockPosition(), SoundEvents.ZOMBIE_INFECT,
                    SoundSource.HOSTILE, 0.8f, 0.7f);
            return;
        }
        UndeadLordRoleData lordData = RoleData.getNullable(UndeadLordRoleData.class, owner);
        if (lordData != null) {
            lordData.addInfection(victim, INFECTION_PER_HIT);
        }

        float damage = (float) NoellesRolesConfig.HANDLER.instance().undeadLordUndeadAttackDamage;
        if (damage > 0f) {
            // 以召唤者身份造成伤害，使击杀归属于亡灵之主（杀手阵营）。
            DamageSource source = owner.damageSources().playerAttack(owner);
            victim.hurt(source, damage);
        }

        serverLevel.playSound(null, victim.blockPosition(), SoundEvents.ZOMBIE_INFECT,
                SoundSource.HOSTILE, 0.8f, 0.7f);
    }

    private void spawnMistParticles(ServerLevel serverLevel) {
        if (this.tickCount % 4 != 0) {
            return;
        }
        // 剩余 30 秒后雾气变淡
        int count = remainingLifetime <= WARNING_FADE_TICKS ? 1 : 2;
        serverLevel.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 1.0D, getZ(), count, 0.3D, 0.5D, 0.3D, 0.01D);
    }

    private void disappear(ServerLevel serverLevel, boolean fullyAged) {
        serverLevel.sendParticles(ParticleTypes.DRAGON_BREATH,
                getX(), getY() + 1.0D, getZ(), 25, 0.4D, 0.8D, 0.4D, 0.02D);
        serverLevel.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 0.7f, 0.6f);
        Player owner = getOwner();
        if (isLordSummoned() && owner != null) {
            UndeadLordRoleData lordData = RoleData.getNullable(UndeadLordRoleData.class, owner);
            if (lordData != null) {
                lordData.onUndeadRemoved(this.getUUID());
            }
        }
        if (isHalloween()) HalloweenCombat.eventUndeadRemoved(this);
        discard();
    }

    /**
     * 被左轮等枪械命中：直接摧毁该亡灵（任意持枪玩家均可击杀）。
     * 由 {@code UndeadGunPayloadMixin} 在收到开枪数据包时调用，绕过常规伤害免疫。
     */
    public void shotByGun(ServerPlayer shooter) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, blockPosition(), SoundEvents.ZOMBIE_DEATH,
                    SoundSource.HOSTILE, 1.0f, 0.7f);
            disappear(serverLevel, false);
        }
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        // 亡灵可被任意攻击方式摧毁（此前仅手枪可击杀），与开枪一致：受到任何伤害即消散。虚空伤害同样致死。
        if (level() instanceof ServerLevel serverLevel) {
            boolean voidDamage = damageSource.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
            serverLevel.playSound(null, blockPosition(), SoundEvents.ZOMBIE_DEATH,
                    SoundSource.HOSTILE, 1.0f, voidDamage ? 0.6f : 0.7f);
            disappear(serverLevel, false);
        }
        return true;
    }

    /** 亡灵永远发光（描边轮廓），便于辨识。 */
    @Override
    public boolean isCurrentlyGlowing() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected boolean canRide(net.minecraft.world.entity.Entity entity) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
        UUID skin = getSkinUuid();
        if (skin != null) {
            tag.putUUID("SkinUUID", skin);
        }
        tag.putInt("RemainingLifetime", remainingLifetime);
        tag.putString("UndeadSource", source.name());
        tag.putBoolean("Halloween", isHalloween()); // 向旧格式保持兼容。
        Vec3 anchor = spawnAnchor == null ? position() : spawnAnchor;
        tag.putDouble("SpawnAnchorX", anchor.x);
        tag.putDouble("SpawnAnchorY", anchor.y);
        tag.putDouble("SpawnAnchorZ", anchor.z);
        tag.putBoolean("ReturningToAnchor", returningToAnchor);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        source = NightUndeadRules.restoreSource(tag.getString("UndeadSource"),
                tag.getBoolean("Halloween"), tag.hasUUID("OwnerUUID"));
        ownerUuid = !isNight() && tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        ownerCache = null;
        // 旧档没有锚点时取本次加载位置，避免回到世界原点。
        spawnAnchor = null;
        if (tag.contains("SpawnAnchorX") && tag.contains("SpawnAnchorY") && tag.contains("SpawnAnchorZ")) {
            Vec3 saved = new Vec3(tag.getDouble("SpawnAnchorX"), tag.getDouble("SpawnAnchorY"),
                    tag.getDouble("SpawnAnchorZ"));
            if (Double.isFinite(saved.x) && Double.isFinite(saved.y) && Double.isFinite(saved.z)) spawnAnchor = saved;
        }
        returningToAnchor = tag.getBoolean("ReturningToAnchor");
        wanderTarget = null;
        setHalloweenTarget(null, 0);
        if (tag.hasUUID("SkinUUID")) {
            this.entityData.set(SKIN_UUID, Optional.of(tag.getUUID("SkinUUID")));
        }
        remainingLifetime = tag.contains("RemainingLifetime") ? tag.getInt("RemainingLifetime") : DEFAULT_LIFETIME;
    }
}
