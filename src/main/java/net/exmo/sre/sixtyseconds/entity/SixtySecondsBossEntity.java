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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 尸潮领主（60s PVE Boss）：带<b>等级 1..5</b> 与 <b>变体</b>，属性/技能随等级与变体增强；
 * 头顶 {@link ServerBossEvent} 血条。技能（服务端 tick 驱动，冷却用 gameTime 时间戳）。
 *
 * <h3>Boss 变体</h3>
 * <ul>
 *   <li><b>破坏者 RAVAGER</b> — 均衡型：震地猛击 / 骇人咆哮 / 尸潮召唤 / 猛冲 / 酸雨（终焉）</li>
 *   <li><b>巨像 COLOSSUS</b> — 重装坦克：强化震地 / 铁壁 / 裂地震波 / 猛冲 / 狂怒被动（低血狂暴）</li>
 *   <li><b>亡灵术士 NECROMANCER</b> — 召唤大师：骨矛 / 生命汲取 / 强化尸潮召唤 / 骨墙 / 亡者大军（终焉）</li>
 *   <li><b>疫病者 PLAGUEBEARER</b> — 毒疫专精：酸液喷吐 / 毒息 / 酸雨齐射 / 腐化光环 / 剧毒新星（终焉）</li>
 *   <li><b>鬼魅 SPECTER</b> — 暗杀刺客：暗影突袭 / 潜行 / 鬼魅瞬击 / 幻影分身 / 死亡标记（终焉）</li>
 * </ul>
 */
public class SixtySecondsBossEntity extends SixtySecondsMonsterEntity {

    // ── Boss 变体枚举 ─────────────────────────────────────────────────
    public enum BossVariant {
        /** 破坏者：均衡型，默认变体，拥有基础技能组 */
        RAVAGER(0, 1.0, 1.0, "sixty_seconds_boss_ravager"),
        /** 巨像：重型坦克，高血量低移速，附带铁壁+狂怒 */
        COLOSSUS(1, 1.5, 0.65, "sixty_seconds_boss_colossus"),
        /** 亡灵术士：召唤专精，本体低血量，强化召唤+生命汲取 */
        NECROMANCER(2, 0.75, 0.9, "sixty_seconds_boss_necromancer"),
        /** 疫病者：毒疫大师，中程酸液+毒息+腐化光环 */
        PLAGUEBEARER(3, 1.05, 0.82, "sixty_seconds_boss_plaguebearer"),
        /** 鬼魅：刺客型，高速度低血量，瞬移+暗影打击 */
        SPECTER(4, 0.7, 1.25, "sixty_seconds_boss_specter");

        public final int id;
        public final double healthMult;
        public final double speedMult;
        public final String textureName;

        BossVariant(int id, double healthMult, double speedMult, String textureName) {
            this.id = id;
            this.healthMult = healthMult;
            this.speedMult = speedMult;
            this.textureName = textureName;
        }

        public static BossVariant byId(int id) {
            for (BossVariant v : values()) {
                if (v.id == id) return v;
            }
            return RAVAGER;
        }

        public String nameKey() {
            return "entity.noellesroles." + textureName;
        }
    }

    private static final EntityDataAccessor<Integer> BOSS_LEVEL =
            SynchedEntityData.defineId(SixtySecondsBossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> APEX =
            SynchedEntityData.defineId(SixtySecondsBossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BOSS_VARIANT =
            SynchedEntityData.defineId(SixtySecondsBossEntity.class, EntityDataSerializers.INT);

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.noellesroles.sixty_seconds_boss"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    // ── 技能冷却（gameTime 时间戳）─────────────────────────────────────
    private long nextSlamTick = 0;
    private long nextRoarTick = 0;
    private long nextSummonTick = 0;
    private long nextChargeTick = 0;
    private long nextBarrageTick = 0;
    // 新增技能冷却
    private long nextDrainTick = 0;       // 生命汲取
    private long nextBreathTick = 0;      // 毒息
    private long nextShadowTick = 0;      // 暗影突袭/鬼魅瞬击
    private long nextSpearTick = 0;       // 骨矛
    private long nextSkinTick = 0;        // 铁壁
    private long nextNovaTick = 0;        // 剧毒新星

    /** 狂怒已激活标记（巨像低血被动） */
    private boolean frenzied = false;
    /** 潜行冷却 / 持续时间管理（鬼魅） */
    private int vanishCooldown = 0;

    public SixtySecondsBossEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BOSS_LEVEL, 1);
        builder.define(APEX, false);
        builder.define(BOSS_VARIANT, BossVariant.RAVAGER.id);
    }

    /** 按 Boss 等级装配（普通尸潮领主）。 */
    public void applyBossLevel(int level) {
        applyBossLevel(level, false, BossVariant.RAVAGER);
    }

    public void applyBossLevel(int level, boolean apex) {
        applyBossLevel(level, apex, BossVariant.RAVAGER);
    }

    /**
     * 按 Boss 等级与变体装配：血量/移速/体型/击退抗性 + 血条标题。
     * {@code apex=true} 为终焉之王终极形态。
     */
    public void applyBossLevel(int level, boolean apex, BossVariant variant) {
        int lvl = Mth.clamp(level, 1, SixtySecondsBalance.BOSS_MAX_LEVEL);
        this.entityData.set(BOSS_LEVEL, lvl);
        this.entityData.set(APEX, apex);
        this.entityData.set(BOSS_VARIANT, variant.id);
        addTag(PVE_TAG);
        double baseHealth = (SixtySecondsBalance.BOSS_BASE_HEALTH
                + SixtySecondsBalance.BOSS_HEALTH_PER_LEVEL * (lvl - 1)) * variant.healthMult;
        double apexMult = apex ? 1.8 : 1.0;
        setAttr(Attributes.MAX_HEALTH, baseHealth * apexMult);
        setAttr(Attributes.MOVEMENT_SPEED, (apex ? 0.27 : 0.24) * variant.speedMult);
        setAttr(Attributes.KNOCKBACK_RESISTANCE, 1.0);
        setAttr(Attributes.SCALE, (apex ? 1.7 : 1.35) + 0.15 * (lvl - 1));
        setHealth(getMaxHealth());
        // 名称
        Component name;
        String baseNameKey = variant == BossVariant.RAVAGER
                ? (apex ? "entity.noellesroles.sixty_seconds_boss_apex_leveled"
                       : "entity.noellesroles.sixty_seconds_boss_leveled")
                : (apex ? "entity.noellesroles.sixty_seconds_boss_variant_apex"
                       : "entity.noellesroles.sixty_seconds_boss_variant_leveled");
        if (variant == BossVariant.RAVAGER) {
            name = Component.translatable(baseNameKey, lvl)
                    .withStyle(apex ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED);
        } else {
            name = Component.translatable(baseNameKey,
                    Component.translatable(variant.nameKey()), lvl)
                    .withStyle(apex ? ChatFormatting.DARK_PURPLE : ChatFormatting.DARK_RED);
        }
        setCustomName(name);
        setCustomNameVisible(true);
        bossEvent.setName(name);
        bossEvent.setColor(apex ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.RED);
        setPersistenceRequired();
        setBattleMob(true);
    }

    public boolean isApex() {
        return this.entityData.get(APEX);
    }

    public BossVariant getBossVariant() {
        return BossVariant.byId(this.entityData.get(BOSS_VARIANT));
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
        int base = SixtySecondsBalance.BOSS_MELEE_INJURY + 4 * (bossLevel() - 1);
        if (frenzied) base = (int)(base * 1.4);
        return base;
    }

    @Override
    public ResourceLocation textureLocation() {
        BossVariant v = getBossVariant();
        if (isApex()) {
            return ResourceLocation.fromNamespaceAndPath("noellesroles",
                    v == BossVariant.RAVAGER
                            ? "textures/entity/sixty_seconds_boss_apex.png"
                            : "textures/entity/" + v.textureName + "_apex.png");
        }
        return ResourceLocation.fromNamespaceAndPath("noellesroles",
                v == BossVariant.RAVAGER
                        ? "textures/entity/sixty_seconds_boss.png"
                        : "textures/entity/" + v.textureName + ".png");
    }

    // ── Boss 血条 ─────────────────────────────────────────────────────
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

    /** 单次受击封顶；巨像额外 30% 减伤。 */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        float capped = Math.min(amount, SixtySecondsBalance.BOSS_MAX_SINGLE_HIT);
        if (getBossVariant() == BossVariant.COLOSSUS) {
            capped *= 0.7F;
        }
        return super.hurt(source, capped);
    }

    // ══════════════════════════════════════════════════════════════════
    //  主 tick：按变体分支
    // ══════════════════════════════════════════════════════════════════
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
        BossVariant variant = getBossVariant();

        // 巨像狂怒被动
        if (variant == BossVariant.COLOSSUS) {
            tickFrenzy();
        }
        // 疫病者腐化光环
        if (variant == BossVariant.PLAGUEBEARER && tickCount % 20 == 0) {
            tickCorruptionAura(serverLevel);
        }
        // 鬼魅潜行冷却
        if (vanishCooldown > 0) vanishCooldown--;

        double distSqr = distanceToSqr(target);

        switch (variant) {
            case RAVAGER -> tickRavager(serverLevel, target, now, lvl, apex, distSqr);
            case COLOSSUS -> tickColossus(serverLevel, target, now, lvl, apex, distSqr);
            case NECROMANCER -> tickNecromancer(serverLevel, target, now, lvl, apex, distSqr);
            case PLAGUEBEARER -> tickPlaguebearer(serverLevel, target, now, lvl, apex, distSqr);
            case SPECTER -> tickSpecter(serverLevel, target, now, lvl, apex, distSqr);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  破坏者 RAVAGER（原版技能组）
    // ══════════════════════════════════════════════════════════════════
    private void tickRavager(ServerLevel serverLevel, LivingEntity target, long now, int lvl, boolean apex, double distSqr) {
        if (now >= nextSlamTick && distSqr <= 5 * 5) {
            slam(serverLevel, now, lvl, 5.5, false);
        } else if (lvl >= 2 && now >= nextRoarTick && distSqr <= 12 * 12) {
            roar(serverLevel, now, lvl, false);
        } else if (apex && now >= nextBarrageTick && distSqr >= 6 * 6 && distSqr <= 30 * 30
                && hasLineOfSight(target)) {
            acidBarrage(serverLevel, now, target, lvl);
        } else if (lvl >= 3 && now >= nextSummonTick) {
            summon(serverLevel, now, lvl, false);
        } else if (lvl >= 4 && now >= nextChargeTick && distSqr >= 8 * 8 && distSqr <= 24 * 24) {
            charge(serverLevel, now, target, 1.6);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  巨像 COLOSSUS
    // ══════════════════════════════════════════════════════════════════
    private void tickColossus(ServerLevel serverLevel, LivingEntity target, long now, int lvl, boolean apex, double distSqr) {
        if (now >= nextSlamTick && distSqr <= 6.5 * 6.5) {
            // 强化震地：更大范围、更高伤害、附带短暂眩晕
            slam(serverLevel, now, lvl, 6.5, true);
        } else if (lvl >= 2 && now >= nextSkinTick && distSqr <= 16 * 16) {
            ironSkin(serverLevel, now, lvl, apex);
        } else if (lvl >= 3 && now >= nextSlamTick && distSqr >= 5 * 5 && distSqr <= 18 * 18) {
            // Lv3 解锁裂地震波
            seismicWave(serverLevel, now, target, lvl);
            nextSlamTick = now + SixtySecondsBalance.BOSS_SLAM_COOLDOWN_TICKS / 2;
        } else if (lvl >= 4 && now >= nextChargeTick && distSqr >= 8 * 8 && distSqr <= 24 * 24) {
            charge(serverLevel, now, target, frenzied ? 2.2 : 1.8);
        } else if (apex && now >= nextRoarTick && distSqr <= 10 * 10) {
            roar(serverLevel, now, lvl, true);
        } else if (now >= nextSlamTick && distSqr <= 5 * 5) {
            slam(serverLevel, now, lvl, 5.5, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  亡灵术士 NECROMANCER
    // ══════════════════════════════════════════════════════════════════
    private void tickNecromancer(ServerLevel serverLevel, LivingEntity target, long now, int lvl, boolean apex, double distSqr) {
        if (now >= nextDrainTick && distSqr <= 10 * 10 && hasLineOfSight(target)
                && getHealth() < getMaxHealth() * 0.7) {
            lifeDrain(serverLevel, now, target, lvl);
        } else if (now >= nextSpearTick && distSqr >= 6 * 6 && distSqr <= 22 * 22
                && hasLineOfSight(target)) {
            boneSpear(serverLevel, now, target, lvl);
        } else if (lvl >= 2 && now >= nextSummonTick) {
            summon(serverLevel, now, lvl, true);
        } else if (lvl >= 3 && now >= nextRoarTick && distSqr <= 12 * 12) {
            roar(serverLevel, now, lvl, false);
        } else if (apex && now >= nextSummonTick && getHealth() < getMaxHealth() * 0.5) {
            // 终焉：低血时亡者大军（翻倍召唤 + 小怪强化光环）
            armyOfTheDead(serverLevel, now, lvl);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  疫病者 PLAGUEBEARER
    // ══════════════════════════════════════════════════════════════════
    private void tickPlaguebearer(ServerLevel serverLevel, LivingEntity target, long now, int lvl, boolean apex, double distSqr) {
        if (now >= nextBreathTick && distSqr <= 7 * 7) {
            toxicBreath(serverLevel, now, target, lvl);
        } else if (now >= nextBarrageTick && distSqr >= 6 * 6 && distSqr <= 26 * 26
                && hasLineOfSight(target)) {
            acidBarrage(serverLevel, now, target, lvl);
        } else if (lvl >= 3 && now >= nextRoarTick && distSqr <= 14 * 14) {
            roar(serverLevel, now, lvl, true); // 毒化咆哮：污染代替扣san
        } else if (apex && now >= nextNovaTick && distSqr <= 12 * 12) {
            toxicNova(serverLevel, now, lvl);
        } else if (lvl >= 4 && now >= nextSummonTick) {
            summon(serverLevel, now, lvl, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  鬼魅 SPECTER
    // ══════════════════════════════════════════════════════════════════
    private void tickSpecter(ServerLevel serverLevel, LivingEntity target, long now, int lvl, boolean apex, double distSqr) {
        if (now >= nextShadowTick && distSqr >= 5 * 5 && distSqr <= 16 * 16
                && hasLineOfSight(target)) {
            shadowStrike(serverLevel, now, target, lvl, apex);
        } else if (lvl >= 2 && now >= nextSlamTick && distSqr <= 4 * 4) {
            // 近身用快速打击替代震地
            shadowFlurry(serverLevel, now, lvl);
        } else if (lvl >= 3 && vanishCooldown <= 0 && getHealth() < getMaxHealth() * 0.65) {
            vanish(serverLevel, now, lvl);
        } else if (apex && now >= nextChargeTick && distSqr >= 8 * 8 && distSqr <= 30 * 30) {
            // 死亡标记：瞬移至目标身后 + 重击
            deathMark(serverLevel, now, target, lvl);
        } else if (lvl >= 4 && now >= nextRoarTick && distSqr <= 10 * 10) {
            roar(serverLevel, now, lvl, false);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  原有技能
    // ══════════════════════════════════════════════════════════════════

    /** 震地猛击：AoE 健康伤害 + 击飞。colossusStun 为巨像强化版（附带短暂减速）。 */
    private void slam(ServerLevel serverLevel, long now, int lvl, double radius, boolean colossusStun) {
        nextSlamTick = now + SixtySecondsBalance.BOSS_SLAM_COOLDOWN_TICKS;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        double r = colossusStun ? radius * 1.15 : radius;
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(),
                6, r * 0.27, 0.3, r * 0.27, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.7F);
        int injury = (SixtySecondsBalance.BOSS_SLAM_INJURY + 4 * (lvl - 1))
                * (colossusStun ? 2 : 1) * (frenzied ? 2 : 1);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > r * r) {
                continue;
            }
            SixtySecondsHealthSystem.applyInjury(player, null, injury);
            Vec3 away = player.position().subtract(position()).normalize();
            double force = colossusStun ? 1.15 : 0.9;
            player.setDeltaMovement(away.x * force, 0.6, away.z * force);
            player.hurtMarked = true;
            if (colossusStun) {
                // 眩晕：极强减速 1.5s
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 + 10, 4));
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 3, 0));
            }
        }
    }

    /** 骇人咆哮：范围内减速 + 黑暗 + 扣 san（或污染）。毒化版对疫病者施加污染替代扣 san。 */
    private void roar(ServerLevel serverLevel, long now, int lvl, boolean toxic) {
        nextRoarTick = now + SixtySecondsBalance.BOSS_ROAR_COOLDOWN_TICKS;
        playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.8F);
        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(),
                3, 0.8, 0.5, 0.8, 0);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > 12 * 12) {
                continue;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0));
            if (toxic) {
                // 毒化版：污染 + 中毒
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                stats.pollution = Math.min(100, stats.pollution + 8 + lvl * 2);
                stats.sync();
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 3, 0));
            } else {
                SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
                stats.sanity = Math.max(0, stats.sanity - (SixtySecondsBalance.BOSS_ROAR_SAN_LOSS + lvl));
                stats.sync();
            }
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.sixty_seconds.boss_roar")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
        }
    }

    /** 尸潮召唤。enhanced 时召唤更强/更多小怪。 */
    private void summon(ServerLevel serverLevel, long now, int lvl, boolean enhanced) {
        nextSummonTick = now + (enhanced
                ? SixtySecondsBalance.BOSS_SUMMON_COOLDOWN_TICKS * 2 / 3
                : SixtySecondsBalance.BOSS_SUMMON_COOLDOWN_TICKS);
        playSound(SoundEvents.ZOMBIE_AMBIENT, 1.4F, 0.5F);
        int count = (enhanced ? 3 : 2) + lvl / 2;
        for (int i = 0; i < count; i++) {
            Variant variant;
            if (enhanced) {
                // 亡灵术士强化召唤：更多样化的怪物
                float r = serverLevel.random.nextFloat();
                if (r < 0.2F) variant = Variant.BRUTE;
                else if (r < 0.45F) variant = Variant.RUNNER;
                else if (r < 0.6F) variant = Variant.STALKER;
                else variant = Variant.SHAMBLER;
            } else {
                variant = serverLevel.random.nextFloat() < 0.35F ? Variant.RUNNER : Variant.SHAMBLER;
            }
            net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.spawnMinion(serverLevel, blockPosition(), variant);
        }
    }

    /** 猛冲。 */
    private void charge(ServerLevel serverLevel, long now, LivingEntity target, double speed) {
        nextChargeTick = now + SixtySecondsBalance.BOSS_CHARGE_COOLDOWN_TICKS;
        playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.3F);
        Vec3 toward = target.position().subtract(position()).normalize();
        setDeltaMovement(toward.x * speed, 0.25, toward.z * speed);
        hurtMarked = true;
        serverLevel.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(),
                12, 0.4, 0.2, 0.4, 0.05);
    }

    /** 酸雨齐射。 */
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
        serverLevel.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getEyeY(), getZ(),
                12, 0.6, 0.4, 0.6, 0.05);
    }

    // ══════════════════════════════════════════════════════════════════
    //  新增技能
    // ══════════════════════════════════════════════════════════════════

    /** 铁壁（巨像）：短暂大幅减伤 + 反伤荆棘。 */
    private void ironSkin(ServerLevel serverLevel, long now, int lvl, boolean apex) {
        nextSkinTick = now + (apex ? 20 * 25 : 20 * 35);
        playSound(SoundEvents.ANVIL_LAND, 0.6F, 1.5F);
        serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT, getX(), getEyeY(), getZ(),
                20, 0.5, 0.8, 0.5, 0.1);
        // 给自身抗性提升 + 反伤（通过额外减伤 + 攻击者受伤实现）
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 6, 2));
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 8, 0));
        // 周围敌人击退（铁壁展开的冲击波）
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > 4 * 4) continue;
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 0.7, 0.35, away.z * 0.7);
            player.hurtMarked = true;
        }
    }

    /** 生命汲取（亡灵术士）：朝目标发射汲取射线，命中持续扣血并治疗自身。 */
    private void lifeDrain(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        nextDrainTick = now + 20 * 16;
        playSound(SoundEvents.WARDEN_HEARTBEAT, 0.5F, 0.3F);
        // 射线特效：连线粒子
        Vec3 from = new Vec3(getX(), getEyeY(), getZ());
        Vec3 to = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        Vec3 step = to.subtract(from).normalize().scale(0.5);
        Vec3 pos = from;
        for (int i = 0; i < 30; i++) {
            pos = pos.add(step);
            serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z,
                    1, 0.1, 0.1, 0.1, 0);
        }
        // 伤害目标 + 治疗自身
        if (target instanceof ServerPlayer player && isValidPrey(player)) {
            int drain = 8 + lvl * 2;
            SixtySecondsHealthSystem.applyInjury(player, null, drain);
            heal(drain * 0.8F);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2));
        }
    }

    /** 骨矛（亡灵术士）：朝目标发射穿透型投射物。 */
    private void boneSpear(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        nextSpearTick = now + 20 * 5;
        playSound(SoundEvents.SKELETON_SHOOT, 0.7F, 0.9F);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 复用酸液投射物作为骨矛（带不同视觉逻辑）
        SixtySecondsAcidSpitEntity spear = new SixtySecondsAcidSpitEntity(serverLevel, this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - spear.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        spear.shoot(dx, dy + horizontal * 0.08, dz, 2.0F, 2.0F);
        spear.setNoGravity(true);
        serverLevel.addFreshEntity(spear);
        serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getEyeY(), getZ(),
                4, 0.2, 0.2, 0.2, 0);
    }

    /** 毒息（疫病者）：前方锥形范围中毒+污染+健康伤害。 */
    private void toxicBreath(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        nextBreathTick = now + 20 * 12;
        playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 0.3F, 0.4F);
        Vec3 facing = getLookAngle().normalize();
        for (int i = 0; i < 20; i++) {
            double spread = (serverLevel.random.nextDouble() - 0.5) * 1.8;
            double dist = 1.0 + i * 2.5 / 20;
            serverLevel.sendParticles(ParticleTypes.ITEM_SLIME,
                    getX() + facing.x * dist + spread, getEyeY() + spread * 0.5,
                    getZ() + facing.z * dist + spread, 1, 0.2, 0.2, 0.2, 0);
        }
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player)) continue;
            Vec3 toPlayer = player.position().subtract(position());
            double dist = toPlayer.length();
            if (dist > 7 || toPlayer.normalize().dot(facing) < 0.35) continue;
            int injury = 6 + lvl * 2;
            SixtySecondsHealthSystem.applyInjury(player, null, injury);
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + 6 + lvl);
            stats.sync();
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 4, lvl > 2 ? 1 : 0));
        }
    }

    /** 腐化光环（疫病者被动）：每 1s 周围玩家获得污染。 */
    private void tickCorruptionAura(ServerLevel serverLevel) {
        double radius = isApex() ? 10.0 : 7.0;
        serverLevel.sendParticles(ParticleTypes.MYCELIUM, getX(), getY() + 0.5, getZ(),
                2, radius * 0.5, 0.3, radius * 0.5, 0);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > radius * radius) continue;
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + 1);
            stats.sync();
        }
    }

    /** 剧毒新星（疫病者终焉）：环形扩散毒弹。 */
    private void toxicNova(ServerLevel serverLevel, long now, int lvl) {
        nextNovaTick = now + 20 * 22;
        playSound(SoundEvents.WITHER_BREAK_BLOCK, 0.7F, 1.2F);
        int shots = 12 + lvl;
        for (int i = 0; i < shots; i++) {
            double angle = (2 * Math.PI / shots) * i;
            SixtySecondsAcidSpitEntity spit = new SixtySecondsAcidSpitEntity(serverLevel, this);
            spit.shoot(Math.cos(angle), 0.1, Math.sin(angle), 0.7F, 8.0F);
            serverLevel.addFreshEntity(spit);
        }
        serverLevel.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(),
                4, 1.5, 0.3, 1.5, 0);
    }

    /** 暗影突袭（鬼魅）：高速冲向目标造成伤害。 */
    private void shadowStrike(ServerLevel serverLevel, long now, LivingEntity target, int lvl, boolean apex) {
        nextShadowTick = now + (apex ? 20 * 6 : 20 * 10);
        playSound(SoundEvents.WARDEN_NEARBY_CLOSE, 0.6F, 1.6F);
        // 瞬移至目标背后
        Vec3 behind = target.position().add(target.getLookAngle().scale(-2.0));
        teleportTo(behind.x, behind.y, behind.z);
        serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(),
                12, 0.3, 0.5, 0.3, 0.1);
        // 伤害目标
        if (target instanceof ServerPlayer player && isValidPrey(player)) {
            int backstab = 14 + lvl * 4;
            if (apex) backstab *= 2;
            SixtySecondsHealthSystem.applyInjury(player, null, backstab);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 + 10, 0));
        }
    }

    /** 暗影连斩（鬼魅近身）：快速多段低伤打击。 */
    private void shadowFlurry(ServerLevel serverLevel, long now, int lvl) {
        nextSlamTick = now + 20 * 8;
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.4F, 1.8F);
        serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK, getX(), getY() + 0.5, getZ(),
                3, 1.5, 0.3, 1.5, 0);
        for (ServerPlayer player : serverLevel.players()) {
            if (!isValidPrey(player) || distanceToSqr(player) > 4 * 4) continue;
            int injury = 4 + lvl;
            // 多段伤害模拟
            for (int i = 0; i < 3; i++) {
                SixtySecondsHealthSystem.applyInjury(player, null, injury);
            }
        }
    }

    /** 潜行（鬼魅）：隐身 + 加速，期间无法被锁定。 */
    private void vanish(ServerLevel serverLevel, long now, int lvl) {
        vanishCooldown = 20 * 30;
        playSound(SoundEvents.WARDEN_DIG, 0.5F, 1.0F);
        addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20 * 5, 0, false, false, false));
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 5, 2, false, false, false));
        serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(),
                20, 0.5, 0.5, 0.5, 0.02);
    }

    /** 死亡标记（鬼魅终焉）：多次瞬移至目标身后打击。 */
    private void deathMark(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        nextChargeTick = now + 20 * 20;
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.8F);
        if (target instanceof ServerPlayer player && isValidPrey(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 4, 0));
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.sixty_seconds.boss_death_mark")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        // 连续 3 次瞬击
        for (int i = 0; i < 3; i++) {
            serverLevel.getServer().tell(new net.minecraft.server.TickTask(
                    i * 6, () -> {
                        if (!isAlive() || target == null || !target.isAlive()) return;
                        Vec3 behind = target.position().add(target.getLookAngle().scale(-2.0));
                        teleportTo(behind.x, behind.y, behind.z);
                        if (target instanceof ServerPlayer p && isValidPrey(p)) {
                            SixtySecondsHealthSystem.applyInjury(p, null, 10 + lvl * 3);
                        }
                        serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(),
                                8, 0.2, 0.4, 0.2, 0.05);
                    }));
        }
    }

    /** 裂地震波（巨像 Lv3+）：朝目标方向释放地面波。 */
    private void seismicWave(ServerLevel serverLevel, long now, LivingEntity target, int lvl) {
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.5F);
        Vec3 dir = target.position().subtract(position()).normalize();
        for (int i = 1; i <= 16; i++) {
            double x = getX() + dir.x * i;
            double z = getZ() + dir.z * i;
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, getY() + 0.1, z,
                    2, 0.3, 0.1, 0.3, 0);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, getY() + 0.1, z,
                    1, 0.2, 0.1, 0.2, 0);
            // 沿线玩家受伤
            for (ServerPlayer player : serverLevel.players()) {
                if (!isValidPrey(player)) continue;
                if (player.distanceToSqr(new Vec3(x, player.getY(), z)) < 2 * 2) {
                    SixtySecondsHealthSystem.applyInjury(player, null,
                            SixtySecondsBalance.BOSS_SLAM_INJURY + lvl * 3);
                    player.setDeltaMovement(dir.x * 0.6, 0.4, dir.z * 0.6);
                    player.hurtMarked = true;
                }
            }
        }
    }

    /** 亡者大军（亡灵术士终焉）：大量召唤 + 光环 buff。 */
    private void armyOfTheDead(ServerLevel serverLevel, long now, int lvl) {
        nextSummonTick = now + SixtySecondsBalance.BOSS_SUMMON_COOLDOWN_TICKS;
        playSound(SoundEvents.WITHER_SPAWN, 0.5F, 0.4F);
        int count = 5 + lvl;
        for (int i = 0; i < count; i++) {
            Variant variant;
            float r = serverLevel.random.nextFloat();
            if (r < 0.3F) variant = Variant.BRUTE;
            else if (r < 0.55F) variant = Variant.RUNNER;
            else if (r < 0.75F) variant = Variant.BLOATER;
            else variant = Variant.STALKER;
            net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.spawnMinion(serverLevel, blockPosition(), variant);
        }
        // 光环：给周围所有自研怪加速+力量
        for (SixtySecondsMonsterEntity mob : serverLevel.getEntitiesOfClass(SixtySecondsMonsterEntity.class,
                getBoundingBox().inflate(16.0))) {
            if (mob == this) continue;
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 12, 1));
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 12, 0));
        }
    }

    /** 狂怒被动（巨像）：低血量时激活，增加伤害和速度。 */
    private void tickFrenzy() {
        boolean shouldFrenzy = getHealth() < getMaxHealth() * 0.35;
        if (shouldFrenzy && !frenzied) {
            frenzied = true;
            playSound(SoundEvents.RAVAGER_ROAR, 1.8F, 0.5F);
            if (level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getEyeY(), getZ(),
                        10, 0.6, 0.5, 0.6, 0.05);
            }
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 60, 1));
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 60, 1));
        }
        if (!shouldFrenzy && frenzied && getHealth() > getMaxHealth() * 0.5) {
            frenzied = false;
            removeEffect(MobEffects.DAMAGE_BOOST);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  死亡 / 存档
    // ══════════════════════════════════════════════════════════════════
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
        tag.putInt("SreBossVariant", getBossVariant().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SreBossLevel")) {
            BossVariant variant = tag.contains("SreBossVariant")
                    ? BossVariant.byId(tag.getInt("SreBossVariant")) : BossVariant.RAVAGER;
            applyBossLevel(tag.getInt("SreBossLevel"), tag.getBoolean("SreBossApex"), variant);
        }
    }
}
