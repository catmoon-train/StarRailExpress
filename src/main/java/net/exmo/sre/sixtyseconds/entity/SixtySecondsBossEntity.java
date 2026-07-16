package net.exmo.sre.sixtyseconds.entity;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 尸潮领主（60s PVE Boss）：带<b>等级 1..5</b>，属性/技能随等级增强；头顶 {@link ServerBossEvent}
 * 血条（进入可视范围的玩家可见）；死亡掉落丰厚物资（{@code SixtySecondsPveSystem.onBossDied} 结算 +
 * 全服播报）。技能（服务端 tick 驱动，冷却用 gameTime 时间戳）：
 * <ol>
 *   <li><b>震地猛击</b>（Lv1+）：近身 AoE 健康伤害 + 击飞；</li>
 *   <li><b>骇人咆哮</b>（Lv2+）：12 格内减速 + 黑暗 + 扣 san；</li>
 *   <li><b>尸潮召唤</b>（Lv3+）：召唤 2+等级/2 只小怪；</li>
 *   <li><b>猛冲</b>（Lv4+）：向目标高速冲撞。</li>
 * </ol>
 * 单次受击伤害封顶 {@link SixtySecondsBalance#BOSS_MAX_SINGLE_HIT}——枪械对普通怪「即死」（1000 伤）
 * 对 Boss 只按封顶生效，避免一枪秒 Boss。
 */
public class SixtySecondsBossEntity extends SixtySecondsMonsterEntity {
    private static final EntityDataAccessor<Integer> BOSS_LEVEL =
            SynchedEntityData.defineId(SixtySecondsBossEntity.class, EntityDataSerializers.INT);
    /** 是否为「终焉之王」终极形态（更高血量、额外酸雨技能、专属贴图与紫色血条）。 */
    private static final EntityDataAccessor<Boolean> APEX =
            SynchedEntityData.defineId(SixtySecondsBossEntity.class, EntityDataSerializers.BOOLEAN);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.noellesroles.sixty_seconds_boss"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    // 技能冷却（gameTime 时间戳）
    private long nextSlamTick = 0;
    private long nextRoarTick = 0;
    private long nextSummonTick = 0;
    private long nextChargeTick = 0;
    private long nextBarrageTick = 0;

    public SixtySecondsBossEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_LEVEL, 1);
        builder.define(APEX, false);
    }

    /** 按 Boss 等级装配（普通尸潮领主）。 */
    public void applyBossLevel(int level) {
        applyBossLevel(level, false);
    }

    /**
     * 按 Boss 等级装配：血量/移速/体型/击退抗性 + 血条标题。
     * {@code apex=true} 为终焉之王终极形态：血量 ×1.8、体型更大、技能冷却更短、解锁酸雨、紫色血条。
     */
    public void applyBossLevel(int level, boolean apex) {
        int lvl = Mth.clamp(level, 1, SixtySecondsBalance.BOSS_MAX_LEVEL);
        this.entityData.set(BOSS_LEVEL, lvl);
        this.entityData.set(APEX, apex);
        addTag(PVE_TAG);
        double baseHealth = SixtySecondsBalance.BOSS_BASE_HEALTH
                + SixtySecondsBalance.BOSS_HEALTH_PER_LEVEL * (lvl - 1);
        setAttr(Attributes.MAX_HEALTH, apex ? baseHealth * 1.8 : baseHealth);
        setAttr(Attributes.MOVEMENT_SPEED, apex ? 0.27 : 0.24);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setAttr(Attributes.SCALE, (apex ? 1.7 : 1.35) + 0.15 * (lvl - 1));
        setHealth(getMaxHealth());
        Component name = Component.translatable(apex
                ? "entity.noellesroles.sixty_seconds_boss_apex_leveled"
                : "entity.noellesroles.sixty_seconds_boss_leveled", lvl)
                .withStyle(apex ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED);
        setCustomName(name);
        setCustomNameVisible(true);
        bossEvent.setName(name);
        bossEvent.setColor(apex ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        setPersistenceRequired();
        setBattleMob(true); // Boss 不因身边无人自散
    }

    public boolean isApex() {
        return this.entityData.get(APEX);
    }

    private void setAttr(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
            double value) {
        var instance = getAttribute(attr);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public int bossLevel() {
        return this.entityData.get(BOSS_LEVEL);
    }

    @Override
    protected int meleeInjury() {
        return SixtySecondsBalance.BOSS_MELEE_INJURY + 4 * (bossLevel() - 1);
    }

    @Override
    public ResourceLocation textureLocation() {
        return ResourceLocation.fromNamespaceAndPath("noellesroles",
                isApex() ? "textures/entity/sixty_seconds_boss_apex.png"
                        : "textures/entity/sixty_seconds_boss.png");
    }

    // ── Boss 血条：进入可视范围的玩家自动加入 ─────────────────────────────
    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    /** 单次受击封顶：枪械 1000 伤「即死」只对普通怪生效，Boss 至多掉封顶值。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, Math.min(amount, SixtySecondsBalance.BOSS_MAX_SINGLE_HIT));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) {
            return;
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        LivingEntity target = getTarget();
        if (target == null || tickCount % 2 != 0) {
            return;
        }
        long now = serverLevel.getGameTime();
        int lvl = bossLevel();
        boolean apex = isApex();
        double distSqr = distanceToSqr(target);
        if (now >= nextSlamTick && distSqr <= 5 * 5) {
            slam(serverLevel, now, lvl);
        } else if (lvl >= 2 && now >= nextRoarTick && distSqr <= 12 * 12) {
            roar(serverLevel, now, lvl);
        } else if (apex && now >= nextBarrageTick && distSqr >= 6 * 6 && distSqr <= 30 * 30
                && hasLineOfSight(target)) {
            // 终焉之王专属：远程酸雨齐射（多发抛物线酸液）
            acidBarrage(serverLevel, now, target, lvl);
        } else if (lvl >= 3 && now >= nextSummonTick) {
            summon(serverLevel, now, lvl);
        } else if (lvl >= 4 && now >= nextChargeTick && distSqr >= 8 * 8 && distSqr <= 24 * 24) {
            charge(serverLevel, now, target);
        }
    }

    /** 终焉之王酸雨：朝目标扇形齐射 4+lvl/2 发酸液（命中扣健康+污染，复用吐酸者投射物）。 */
    private void acidBarrage(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        nextBarrageTick = now + SixtySecondsBalance.BOSS_ROAR_COOLDOWN_TICKS;
        playSound(SoundEvents.LLAMA_SPIT, 1.6F, 0.5F);
        int shots = 4 + lvl / 2;
        for (int i = 0; i < shots; i++) {
            SixtySecondsAcidSpitEntity spit = new SixtySecondsAcidSpitEntity(serverLevel, this);
            double dx = target.getX() - getX() + (serverLevel.random.nextDouble() - 0.5) * 4.0;
            double dy = target.getY(0.4) - spit.getY();
            double dz = target.getZ() - getZ() + (serverLevel.random.nextDouble() - 0.5) * 4.0;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            spit.shoot(dx, dy + horizontal * 0.14, dz, 1.0F, 6.0F);
            serverLevel.addFreshEntity(spit);
        }
        serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getEyeY(), getZ(), 12, 0.6, 0.4, 0.6, 0.05);
    }

    /** 震地猛击：5.5 格 AoE 健康伤害 + 击飞。 */
    private void slam(ServerLevel serverLevel, long now, int lvl) {
        nextSlamTick = now + SixtySecondsBalance.BOSS_SLAM_COOLDOWN_TICKS;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 6, 1.5, 0.3, 1.5, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.7F);
        int injury = SixtySecondsBalance.BOSS_SLAM_INJURY + 4 * (lvl - 1);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > 5.5 * 5.5) {
                continue;
            }
            SixtySecondsHealthSystem.applyInjury(player, null, injury);
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 0.9, 0.6, away.z * 0.9);
            player.hurtMarked = true; // 强制同步击飞速度
        }
    }

    /** 骇人咆哮：12 格内减速 + 黑暗 + 扣 san。 */
    private void roar(ServerLevel serverLevel, long now, int lvl) {
        nextRoarTick = now + SixtySecondsBalance.BOSS_ROAR_COOLDOWN_TICKS;
        playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.8F);
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(), 3, 0.8, 0.5, 0.8, 0);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > 12 * 12) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.sanity = Math.max(0, stats.sanity - (SixtySecondsBalance.BOSS_ROAR_SAN_LOSS + lvl));
            stats.sync();
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.sixty_seconds.boss_roar")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** 尸潮召唤：身边召 2+lvl/2 只小怪（登记进 PVE 追踪表随局清理）。 */
    private void summon(ServerLevel serverLevel, long now, int lvl) {
        nextSummonTick = now + SixtySecondsBalance.BOSS_SUMMON_COOLDOWN_TICKS;
        playSound(SoundEvents.ZOMBIE_AMBIENT, 1.4F, 0.5F);
        int count = 2 + lvl / 2;
        for (int i = 0; i < count; i++) {
            Variant variant = serverLevel.random.nextFloat() < 0.35F ? Variant.RUNNER : Variant.SHAMBLER;
            net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.spawnMinion(serverLevel, blockPosition(), variant);
        }
    }

    /** 猛冲：向目标水平冲撞。 */
    private void charge(ServerLevel serverLevel, long now, LivingEntity target) {
        nextChargeTick = now + SixtySecondsBalance.BOSS_CHARGE_COOLDOWN_TICKS;
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.3F);
        Vec3 toward = target.position().subtract(position()).normalize();
        setDeltaMovement(toward.x * 1.6, 0.25, toward.z * 1.6);
        hurtMarked = true;
        serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 12, 0.4, 0.2, 0.4, 0.05);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        if (level() instanceof ServerLevel serverLevel) {
            net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.onBossDied(serverLevel, this, damageSource);
        }
        bossEvent.removeAllPlayers();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SreBossLevel", bossLevel());
        tag.putBoolean("SreBossApex", isApex());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SreBossLevel")) {
            applyBossLevel(tag.getInt("SreBossLevel"), tag.getBoolean("SreBossApex"));
        }
    }
}
