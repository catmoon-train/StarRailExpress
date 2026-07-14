package net.exmo.sre.sixtyseconds.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * 60s 模式自研怪物基类（替代原版僵尸夜袭者）：
 * <ul>
 *   <li><b>和平难度存活</b>：本模式全程 PEACEFUL，覆写 {@link #shouldDespawnInPeaceful()}=false
 *       即免疫 {@code Mob.checkDespawn} 的和平清除（自研实体无需 {@code SixtySecondsMobPeacefulMixin} 放行）。</li>
 *   <li><b>攻击玩家</b>：和平难度下原版怪对玩家伤害恒为 0（{@code Player.hurt} 按难度清零、
 *       ALLOW_DAMAGE 链不触发），故 {@link #doHurtTarget} 绕过原版伤害链、直接
 *       {@link SixtySecondsHealthSystem#applyInjury} 结算健康伤害（走倒地/处决路径）。</li>
 *   <li><b>变体</b>：{@link Variant} 四种基础怪（拖行者/奔跑者/重锤兽/吐酸者），一种实体类型 +
 *       SynchedEntityData 同步变体号，客户端渲染按变体换贴图（{@code SixtySecondsMonsterRenderer}）。</li>
 *   <li><b>自清理</b>：模式结束（isActive=false）自毁；非战场怪（夜袭者见
 *       {@link #setBattleMob}）身边 64 格无人持续 1 分钟自散，防游荡怪堆积。</li>
 * </ul>
 * 继承 {@link Zombie}：夜袭系统（{@code SixtySecondsDefenseSystem}）按 Zombie 类型追踪冲门/打路障，
 * 自研怪可无缝接入原有冲门/掉落/召唤哨逻辑。
 */
public class SixtySecondsMonsterEntity extends Zombie {
    /** 所有 60s 自研怪共用 tag（枪械/手雷/清场兜底按它识别；实体自身逻辑用 instanceof）。 */
    public static final String PVE_TAG = "sixty_seconds_pve_monster";

    private static final EntityDataAccessor<Integer> VARIANT =
            SynchedEntityData.defineId(SixtySecondsMonsterEntity.class, EntityDataSerializers.INT);

    /** 变体：生命 / 移速 / 对玩家健康伤害 / 对门（路障）每秒伤害 / 贴图名。 */
    public enum Variant {
        /** 拖行者：基础近战怪，中速中血。 */
        SHAMBLER(0, 24.0, 0.21, 16, 2, "sixty_seconds_shambler"),
        /** 奔跑者：速度快、血薄、伤害低，负责施压。 */
        RUNNER(1, 14.0, 0.30, 10, 1, "sixty_seconds_runner"),
        /** 重锤兽：慢速高血高伤，破门主力。 */
        BRUTE(2, 60.0, 0.18, 30, 5, "sixty_seconds_brute"),
        /** 吐酸者：中距吐酸（酸液投射物，命中扣健康+污染），近战弱。 */
        SPITTER(3, 18.0, 0.22, 8, 1, "sixty_seconds_spitter");

        public final int id;
        public final double health;
        public final double speed;
        /** 近战命中玩家扣的健康值。 */
        public final int injury;
        /** 对家门/路障每秒伤害（夜袭时由 DefenseSystem 结算）。 */
        public final int doorDps;
        public final String textureName;

        Variant(int id, double health, double speed, int injury, int doorDps, String textureName) {
            this.id = id;
            this.health = health;
            this.speed = speed;
            this.injury = injury;
            this.doorDps = doorDps;
            this.textureName = textureName;
        }

        public static Variant byId(int id) {
            for (Variant variant : values()) {
                if (variant.id == id) {
                    return variant;
                }
            }
            return SHAMBLER;
        }

        public String nameKey() {
            return "entity.noellesroles." + textureName;
        }
    }

    /** 战场怪（夜袭者/召唤哨）：无人也不自散（战场区块常加载、离线也冲门）。 */
    private boolean battleMob = false;
    /** 身边无人累计 tick（非战场怪 1 分钟自散）。 */
    private int lonelyTicks = 0;
    /** 吐酸冷却（吐酸者变体）。 */
    private int spitCooldown = 0;

    public SixtySecondsMonsterEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(VARIANT, Variant.SHAMBLER.id);
    }

    /** 生成后按变体装配属性（生命/移速）与名字；{@code healthMult} 供区域等级/强度档缩放。 */
    public void applyVariant(Variant variant, double healthMult, double speedMult) {
        this.entityData.set(VARIANT, variant.id);
        addTag(PVE_TAG);
        double health = variant.health * healthMult;
        var maxHealth = getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(health);
        }
        var moveSpeed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.setBaseValue(variant.speed * speedMult);
        }
        setHealth((float) health);
        setCustomName(net.minecraft.network.chat.Component.translatable(variant.nameKey()));
        setPersistenceRequired();
    }

    public Variant getVariant() {
        return Variant.byId(this.entityData.get(VARIANT));
    }

    public void setBattleMob(boolean battleMob) {
        this.battleMob = battleMob;
    }

    /** 客户端渲染取贴图（按变体）。 */
    public ResourceLocation textureLocation() {
        return ResourceLocation.fromNamespaceAndPath("noellesroles",
                "textures/entity/" + getVariant().textureName + ".png");
    }

    // ── 和平难度 / 持久化：本模式全程 PEACEFUL，自研怪不被清、不换水生形态、不晒伤 ────
    @Override
    public boolean shouldDespawnInPeaceful() {
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
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    /** 近战命中：绕过原版伤害链（和平难度清零），直接按变体伤害结算健康值。 */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (level() instanceof ServerLevel serverLevel && SixtySecondsMod.isActive(serverLevel)
                && target instanceof ServerPlayer player) {
            if (!isValidPrey(player)) {
                setTarget(null);
                return false;
            }
            swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.3F, 1.4F);
            SixtySecondsHealthSystem.applyInjury(player, null, meleeInjury());
            return true;
        }
        return super.doHurtTarget(target);
    }

    /** 本次近战命中扣的健康值（Boss 覆写为按等级缩放）。 */
    protected int meleeInjury() {
        return getVariant().injury;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 模式已结束：全部自毁（免游戏外残留；reset 的按 tag 清扫是二重兜底）
        if (!SixtySecondsMod.isActive(serverLevel)) {
            discard();
            return;
        }
        // 目标有效性：倒地者（怪打不动）/变怪玩家/创造/旁观 不作为追击目标
        if (getTarget() instanceof ServerPlayer targetPlayer && !isValidPrey(targetPlayer)) {
            setTarget(null);
        }
        if (spitCooldown > 0) {
            spitCooldown--;
        }
        if (getVariant() == Variant.SPITTER) {
            tickSpit(serverLevel);
        }
        // 非战场怪：身边 64 格无人累计 1 分钟自散（防探索区游荡怪越攒越多）
        if (!battleMob && tickCount % 20 == 0) {
            lonelyTicks = serverLevel.getNearestPlayer(this, 64) == null ? lonelyTicks + 20 : 0;
            if (lonelyTicks >= SixtySecondsBalance.PVE_LONELY_DESPAWN_TICKS) {
                discard();
            }
        }
    }

    /** 吐酸者：目标在 4~14 格且可视时朝其吐酸（抛物线投射物，命中扣健康+污染）。 */
    private void tickSpit(ServerLevel serverLevel) {
        LivingEntity target = getTarget();
        if (target == null || spitCooldown > 0) {
            return;
        }
        double distSqr = distanceToSqr(target);
        if (distSqr < 4 * 4 || distSqr > 14 * 14 || !hasLineOfSight(target)) {
            return;
        }
        spitCooldown = SixtySecondsBalance.PVE_SPIT_COOLDOWN_TICKS;
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        SixtySecondsAcidSpitEntity spit = new SixtySecondsAcidSpitEntity(serverLevel, this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - spit.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spit.shoot(dx, dy + horizontal * 0.12, dz, 1.1F, 4.0F);
        playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.7F);
        serverLevel.addFreshEntity(spit);
    }

    /** 可被追击/伤害的玩家：存活生存、未倒地、未变怪。 */
    public static boolean isValidPrey(ServerPlayer player) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        return !stats.downed && !stats.monster;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SreVariant", this.entityData.get(VARIANT));
        tag.putBoolean("SreBattleMob", battleMob);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(VARIANT, tag.getInt("SreVariant"));
        battleMob = tag.getBoolean("SreBattleMob");
    }
}
