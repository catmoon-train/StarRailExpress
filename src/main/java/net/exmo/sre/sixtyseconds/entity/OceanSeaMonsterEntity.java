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
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * 海洋巨兽：克拉肯/海蛇/利维坦——带 Boss 血条的深海霸主。
 *
 * <h3>变体一览</h3>
 * <table>
 *   <tr><th>变体</th><th>半径</th><th>生命</th><th>伤害</th><th>技能组</th></tr>
 *   <tr><td>KRAKEN 克拉肯</td><td>~10格</td><td>600</td><td>35</td><td>触手扫击、墨云、漩涡拖拽</td></tr>
 *   <tr><td>SERPENT 海蛇</td><td>~15格</td><td>1200</td><td>50</td><td>水炮远程、毒息、尾部击飞</td></tr>
 *   <tr><td>LEVIATHAN 利维坦</td><td>~20格</td><td>2500</td><td>70</td><td>水流爆破、咆哮、召唤鱼群、终焉漩涡</td></tr>
 * </table>
 */
public class OceanSeaMonsterEntity extends OceanCreatureEntity {

    public enum Variant {
        /** 克拉肯：触手扫击 + 墨云致盲 + 漩涡拖拽 */
        KRAKEN(0, 600.0, 0.16, 35, 10.0F, "ocean_kraken"),
        /** 海蛇：远程水炮 + 毒息 + 尾部击飞 */
        SERPENT(1, 1200.0, 0.14, 50, 15.0F, "ocean_serpent"),
        /** 利维坦：全技能 + 咆哮 + 召唤鱼群 + 终焉漩涡 */
        LEVIATHAN(2, 2500.0, 0.12, 70, 20.0F, "ocean_leviathan");

        public final int id;
        public final double health;
        public final double speed;
        public final int injury;
        public final float scale;
        public final String textureName;

        Variant(int id, double health, double speed, int injury, float scale, String textureName) {
            this.id = id;
            this.health = health;
            this.speed = speed;
            this.injury = injury;
            this.scale = scale;
            this.textureName = textureName;
        }

        public static Variant byId(int id) {
            for (Variant v : values()) if (v.id == id) return v;
            return KRAKEN;
        }

        public String nameKey() {
            return "entity.noellesroles." + textureName;
        }
    }

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.noellesroles.ocean_sea_monster"),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_20);

    // 技能冷却
    private long nextSlamTick = 0;      // 触手扫/尾部击
    private long nextInkTick = 0;       // 墨云/毒息
    private long nextPullTick = 0;      // 漩涡拖拽
    private long nextBoltTick = 0;      // 水炮/远程
    private long nextSummonTick = 0;    // 召唤鱼群（利维坦）
    private long nextRoarTick = 0;      // 咆哮（利维坦）

    // ── 压迫力：狂暴相位 + 深海压迫光环 + 预警式终极技 ──────────────────────────
    /** 血量 ≤ 此比例进入<b>狂暴</b>：冷却缩短、伤害/移速提升、Boss 血条转红。 */
    private static final float ENRAGE_HP_FRAC = 0.40F;
    /** 狂暴时技能冷却倍率 / 伤害倍率 / 移速倍率。 */
    private static final double ENRAGE_CD_MULT = 0.6;
    private static final double ENRAGE_INJURY_MULT = 1.25;
    private static final double ENRAGE_SPEED_MULT = 1.4;
    /** 深海压迫光环半径（按变体）：靠近即受压，不必被锁定。 */
    private static final double AURA_R_KRAKEN = 18.0;
    private static final double AURA_R_SERPENT = 24.0;
    private static final double AURA_R_LEVIATHAN = 32.0;
    private static final int AURA_INTERVAL = 40; // 每 2 秒一次压迫脉冲

    /** 已进入狂暴。 */
    private boolean enraged = false;
    /** 深海压迫光环下次脉冲时间。 */
    private long nextAuraTick = 0;
    /** 终极技共享冷却。 */
    private long nextUltTick = 0;
    /** 提示节流：动作栏压迫提示下次可发时间。 */
    private long nextAuraMsgTick = 0;

    /** 当前引导中的终极技：0=无 1=触手林（克拉肯）2=缠绕绞杀（海蛇）3=终焉漩涡（利维坦）。 */
    private int channel = 0;
    /** 引导结束时间。 */
    private long channelEndTick = 0;
    /** 触手林：开场捕获的各玩家落点标记（引导期冒泡预警，结束齐爆）。 */
    private final java.util.List<Vec3> fieldMarks = new java.util.ArrayList<>();
    /** 缠绕绞杀：被缠住的目标。 */
    private java.util.UUID constrictTarget = null;
    /** 缠绕绞杀：已缠绕的秒数（伤害逐秒递增）。 */
    private int constrictSeconds = 0;
    /** 狂暴反击：上次反击时间（防连触）。 */
    private long nextRetaliateTick = 0;

    public OceanSeaMonsterEntity(EntityType<? extends OceanCreatureEntity> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 0;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new OceanSwimGoal(this, 0.5, 40));
        this.goalSelector.addGoal(1, new OceanMeleeAttackGoal(this, 0.8, true));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ServerPlayer.class,
                10, true, false, p -> isValidOceanPrey((ServerPlayer) p)));
    }

    public void applyVariant(Variant variant) {
        applyVariant(variant.id, variant.health, variant.speed, variant.scale, variant.nameKey());
        // Boss血条名称
        Component name = Component.translatable(variant.nameKey())
                .withStyle(ChatFormatting.DARK_PURPLE);
        setCustomName(name);
        setCustomNameVisible(true);
        bossEvent.setName(name);
        bossEvent.setColor(variant == Variant.LEVIATHAN ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE);
    }

    public Variant getVariant() {
        return Variant.byId(getVariantId());
    }

    public ResourceLocation textureLocation() {
        return Noellesroles.id("textures/entity/" + getVariant().textureName + ".png");
    }

    // ── Boss 血条 ─────────────────────────────────────────────────
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

    /** 本次技能应扣的健康值（狂暴时 ×{@link #ENRAGE_INJURY_MULT}）。 */
    private int injuryNow(int base) {
        return enraged ? (int) Math.round(base * ENRAGE_INJURY_MULT) : base;
    }

    /** 冷却按狂暴缩短。 */
    private long cd(long baseTicks) {
        return enraged ? (long) (baseTicks * ENRAGE_CD_MULT) : baseTicks;
    }

    private double auraRadius() {
        return switch (getVariant()) {
            case KRAKEN -> AURA_R_KRAKEN;
            case SERPENT -> AURA_R_SERPENT;
            case LEVIATHAN -> AURA_R_LEVIATHAN;
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 海怪受伤封顶保护
        float capped = Math.min(amount, getVariant() == Variant.LEVIATHAN ? 120.0F : 80.0F);
        boolean result = super.hurt(source, capped);
        // 狂暴反击：受到重击（≥封顶一半）时立刻爆发一圈击退（防连触，2s 一次）
        if (result && enraged && capped >= (getVariant() == Variant.LEVIATHAN ? 60.0F : 40.0F)
                && level() instanceof ServerLevel sl && sl.getGameTime() >= nextRetaliateTick) {
            nextRetaliateTick = sl.getGameTime() + 40;
            enrageRetaliate(sl);
        }
        return result;
    }

    /** 狂暴反击：小范围击退 + 扎刺伤害，惩罚贴脸输出。 */
    private void enrageRetaliate(ServerLevel sl) {
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 0.8F, 1.2F);
        double r = 6.0;
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1.0, getZ(),
                20, r * 0.5, 0.8, r * 0.5, 0.05);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury / 2));
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 1.0, 0.6, away.z * 1.0);
            player.hurtMarked = true;
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(level() instanceof ServerLevel) || !(target instanceof ServerPlayer player)
                || !isValidOceanPrey(player)) {
            setTarget(null);
            return false;
        }
        player.invulnerableTime = 10;
        playSound(SoundEvents.ELDER_GUARDIAN_HURT, 0.5F, 0.7F);
        SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury));
        // 海怪攻击附加污染
        SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
        stats.pollution = Math.min(100, stats.pollution + 5);
        stats.sync();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) return;
        bossEvent.setProgress(getHealth() / getMaxHealth());

        long now = serverLevel.getGameTime();

        // ① 狂暴相位（每 tick 便宜地判一次）：血量首次跌破阈值即转狂暴
        if (!enraged && getHealth() <= getMaxHealth() * ENRAGE_HP_FRAC) {
            enterEnrage(serverLevel, now);
        }

        // ② 引导中的终极技独占：期间不放其它技能，持续演出，到点结算
        if (channel != 0) {
            tickChannel(serverLevel, now);
            return;
        }

        // ③ 深海压迫光环（被动，与是否锁定无关）：靠近就受压
        if (now >= nextAuraTick) {
            nextAuraTick = now + AURA_INTERVAL;
            tickOppressionAura(serverLevel, now);
        }

        LivingEntity target = getTarget();
        if (target == null || tickCount % 2 != 0) return;

        double distSqr = distanceToSqr(target);

        // ④ 终极技优先（共享长冷却；狂暴时更快回转、命中更痛）
        if (now >= nextUltTick && tryStartUltimate(serverLevel, target, now, distSqr)) {
            return;
        }

        switch (getVariant()) {
            case KRAKEN -> tickKraken(serverLevel, target, now, distSqr);
            case SERPENT -> tickSerpent(serverLevel, target, now, distSqr);
            case LEVIATHAN -> tickLeviathan(serverLevel, target, now, distSqr);
        }
    }

    // ═══════════ 狂暴相位 ═══════════

    /** 进入狂暴：血条转红改名、提速、全屏警告冲击波 + 恐惧减益。 */
    private void enterEnrage(ServerLevel sl, long now) {
        enraged = true;
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * ENRAGE_SPEED_MULT);
            this.swimSpeed = speed.getBaseValue(); // 让基类的回水还原用狂暴后的游速
        }
        Component name = Component.translatable(getVariant().nameKey())
                .append(Component.translatable("entity.noellesroles.ocean.enrage_suffix"))
                .withStyle(ChatFormatting.DARK_RED);
        setCustomName(name);
        bossEvent.setName(name);
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);

        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.2F, 0.5F);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(), 8, 1.5, 1.0, 1.5, 0);
        double r = auraRadius();
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 4, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 5, 0));
            player.displayClientMessage(Component.translatable("message.noellesroles.ocean.enrage")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ═══════════ 深海压迫光环 ═══════════

    /**
     * 被动压迫：光环内的水中玩家周期性承受「深海重压」——减速 + 挖掘疲劳（下潜/挣扎更难），
     * 掉一点理智，周身涌暗流粒子；靠近本身就是煎熬，不必被锁定为目标。
     */
    private void tickOppressionAura(ServerLevel sl, long now) {
        double r = auraRadius();
        int sanLoss = switch (getVariant()) {
            case KRAKEN -> 1;
            case SERPENT -> 1;
            case LEVIATHAN -> 2;
        };
        boolean anyHit = false;
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || !player.isInWater()
                    || distanceToSqr(player) > r * r) {
                continue;
            }
            anyHit = true;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    AURA_INTERVAL + 20, enraged ? 1 : 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,
                    AURA_INTERVAL + 20, 1, false, false, true));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.sanity = Math.max(0, stats.sanity - sanLoss);
            stats.sync();
            sl.sendParticles(ParticleTypes.CURRENT_DOWN, player.getX(), player.getY() + 0.5,
                    player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
            if (now >= nextAuraMsgTick) {
                player.displayClientMessage(Component.translatable("message.noellesroles.ocean.aura")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
            }
        }
        if (anyHit && now >= nextAuraMsgTick) {
            nextAuraMsgTick = now + 20 * 6; // 提示 6 秒一次，不刷屏
            playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.4F, 0.5F);
        }
    }

    // ═══════════ 克拉肯 ═══════════
    private void tickKraken(ServerLevel sl, LivingEntity target, long now, double distSqr) {
        if (now >= nextSlamTick && distSqr <= 8 * 8) {
            tentacleSlam(sl, now);
        } else if (now >= nextPullTick && distSqr > 4 * 4 && distSqr <= 18 * 18) {
            vortexPull(sl, now, target);
        } else if (now >= nextInkTick && distSqr <= 14 * 14) {
            inkCloud(sl, now);
        }
    }

    // ═══════════ 海蛇 ═══════════
    private void tickSerpent(ServerLevel sl, LivingEntity target, long now, double distSqr) {
        if (now >= nextBoltTick && distSqr > 6 * 6 && distSqr <= 26 * 26 && hasLineOfSight(target)) {
            waterBolt(sl, now, target);
        } else if (now >= nextInkTick && distSqr <= 10 * 10) {
            toxicFume(sl, now);
        } else if (now >= nextSlamTick && distSqr <= 10 * 10) {
            tailSweep(sl, now);
        }
    }

    // ═══════════ 利维坦 ═══════════
    private void tickLeviathan(ServerLevel sl, LivingEntity target, long now, double distSqr) {
        if (now >= nextSlamTick && distSqr <= 10 * 10) {
            tentacleSlam(sl, now);
        } else if (now >= nextBoltTick && distSqr > 8 * 8 && distSqr <= 30 * 30 && hasLineOfSight(target)) {
            waterBolt(sl, now, target);
        } else if (now >= nextRoarTick && distSqr <= 20 * 20) {
            leviathanRoar(sl, now);
        } else if (now >= nextSummonTick) {
            summonMinions(sl, now);
        } else if (now >= nextPullTick && distSqr > 4 * 4 && distSqr <= 22 * 22) {
            vortexPull(sl, now, target);
        }
    }

    // ═══════════ 终极技（预警式，引导独占）═══════════

    /**
     * 尝试开一个终极技（每变体一个招牌）：起手即进入引导（{@link #channel}），
     * 引导期演出预警、到点在 {@link #tickChannel} 结算。返回是否开启。
     */
    private boolean tryStartUltimate(ServerLevel sl, LivingEntity target, long now, double distSqr) {
        switch (getVariant()) {
            case KRAKEN -> {
                if (distSqr <= 22 * 22) {
                    startTentacleField(sl, now);
                    return true;
                }
            }
            case SERPENT -> {
                if (distSqr <= 28 * 28 && target instanceof ServerPlayer p && isValidOceanPrey(p)) {
                    startConstrict(sl, now, p);
                    return true;
                }
            }
            case LEVIATHAN -> {
                startMaelstrom(sl, now);
                return true;
            }
        }
        return false;
    }

    /** 引导期每 tick 推进：演出预警 / 持续作用；到点结算收尾。 */
    private void tickChannel(ServerLevel sl, long now) {
        switch (channel) {
            case 1 -> tentacleFieldTelegraph(sl);
            case 2 -> constrictTick(sl, now);
            case 3 -> maelstromTick(sl, now);
            default -> { }
        }
        if (now >= channelEndTick) {
            finishChannel(sl);
        }
    }

    private void finishChannel(ServerLevel sl) {
        switch (channel) {
            case 1 -> tentacleFieldErupt(sl);
            case 2 -> { /* 缠绕自然松开，无额外收尾 */ }
            case 3 -> maelstromDetonate(sl);
            default -> { }
        }
        channel = 0;
        fieldMarks.clear();
        constrictTarget = null;
        constrictSeconds = 0;
    }

    // ── 克拉肯招牌：触手林（各玩家脚下预警 1.5s 后齐爆）──────────────────
    private void startTentacleField(ServerLevel sl, long now) {
        channel = 1;
        channelEndTick = now + 30; // 1.5s 预警
        nextUltTick = now + cd(20 * 24);
        fieldMarks.clear();
        for (ServerPlayer player : sl.players()) {
            if (isValidOceanPrey(player) && distanceToSqr(player) <= 24 * 24) {
                fieldMarks.add(player.position());
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.ocean.tentacle_field").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
        playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT_LAND, 1.0F, 0.6F);
    }

    private void tentacleFieldTelegraph(ServerLevel sl) {
        // 每个标记处冒起水柱预警——脚下起水柱就是「快躲开」的信号
        for (Vec3 mark : fieldMarks) {
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, mark.x, mark.y, mark.z,
                    6, 0.3, 1.2, 0.3, 0.03);
        }
    }

    private void tentacleFieldErupt(ServerLevel sl) {
        playSound(SoundEvents.ELDER_GUARDIAN_HURT, 1.2F, 0.4F);
        double hitR = 3.5;
        for (Vec3 mark : fieldMarks) {
            sl.sendParticles(ParticleTypes.EXPLOSION, mark.x, mark.y + 0.5, mark.z, 3, 0.6, 0.4, 0.6, 0);
            sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, mark.x, mark.y, mark.z, 30, 0.5, 2.0, 0.5, 0.1);
            for (ServerPlayer player : sl.players()) {
                if (!isValidOceanPrey(player)) continue;
                if (player.position().distanceToSqr(mark) <= hitR * hitR) {
                    SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury + 10));
                    player.setDeltaMovement(player.getDeltaMovement().x, 1.1, player.getDeltaMovement().z);
                    player.hurtMarked = true;
                }
            }
        }
    }

    // ── 海蛇招牌：缠绕绞杀（锁定一人，3s 反复拖拽 + 逐秒加伤）───────────────
    private void startConstrict(ServerLevel sl, long now, ServerPlayer p) {
        channel = 2;
        channelEndTick = now + 60; // 3s
        nextUltTick = now + cd(20 * 20);
        constrictTarget = p.getUUID();
        constrictSeconds = 0;
        playSound(SoundEvents.PHANTOM_BITE, 0.9F, 0.6F);
        p.displayClientMessage(Component.translatable("message.noellesroles.ocean.constrict")
                .withStyle(ChatFormatting.DARK_GREEN), true);
    }

    private void constrictTick(ServerLevel sl, long now) {
        ServerPlayer p = constrictTarget == null ? null
                : sl.getServer().getPlayerList().getPlayer(constrictTarget);
        if (p == null || !isValidOceanPrey(p)) {
            channelEndTick = now; // 目标没了：立即松开
            return;
        }
        // 持续把目标拖向自身（挣不脱），每秒结算一次递增伤害 + 缠绕粒子
        Vec3 toward = position().subtract(p.position()).normalize();
        p.setDeltaMovement(toward.x * 0.35, toward.y * 0.2 + 0.02, toward.z * 0.35);
        p.hurtMarked = true;
        sl.sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(),
                4, 0.4, 0.6, 0.4, 0.01);
        if (now % 20 == 0) {
            constrictSeconds++;
            // 逐秒加伤：第 1/2/3 秒 = 基础 ×0.6 / ×0.9 / ×1.3，绞得越久越痛
            double ramp = 0.3 + 0.3 * constrictSeconds;
            SixtySecondsHealthSystem.applyInjury(p, null, injuryNow((int) (getVariant().injury * ramp)));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(p);
            stats.pollution = Math.min(100, stats.pollution + 4);
            stats.sync();
            playSound(SoundEvents.PHANTOM_BITE, 0.6F, 0.5F);
        }
    }

    // ── 利维坦招牌：终焉漩涡（4s 引导，卷入全域玩家、逐秒加伤，结束引爆）──────
    private void startMaelstrom(ServerLevel sl, long now) {
        channel = 3;
        channelEndTick = now + 80; // 4s
        nextUltTick = now + cd(20 * 30);
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.4F, 0.4F);
        double r = 30.0;
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 6, 0));
            player.displayClientMessage(Component.translatable("message.noellesroles.ocean.maelstrom")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    private void maelstromTick(ServerLevel sl, long now) {
        double r = 30.0;
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY(), getZ(),
                10, 2.0, 1.5, 2.0, 0.05);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            // 强力卷向中心（比普通漩涡更凶，几乎挣不脱）
            Vec3 toward = position().subtract(player.position()).normalize();
            player.setDeltaMovement(player.getDeltaMovement().scale(0.6).add(
                    toward.x * 0.35, toward.y * 0.12, toward.z * 0.35));
            player.hurtMarked = true;
            if (now % 20 == 0) {
                SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury / 2));
            }
        }
    }

    private void maelstromDetonate(ServerLevel sl) {
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.7F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.0, getZ(), 3, 2.0, 1.0, 2.0, 0);
        double r = 14.0;
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury + 20));
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 1.6, 1.0, away.z * 1.6);
            player.hurtMarked = true;
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.sanity = Math.max(0, stats.sanity - 8);
            stats.sync();
        }
    }

    // ── 触手扫击（克拉肯/利维坦）────────────────────────────────
    private void tentacleSlam(ServerLevel sl, long now) {
        nextSlamTick = now + cd(20 * 9);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        double r = getVariant() == Variant.LEVIATHAN ? 10.0 : 7.0;
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1.0, getZ(),
                12, r * 0.4, 0.6, r * 0.4, 0);
        playSound(SoundEvents.ELDER_GUARDIAN_HURT, 0.7F, 0.5F);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury));
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 0.7, 0.4, away.z * 0.7);
            player.hurtMarked = true;
        }
    }

    // ── 尾部击飞（海蛇）───────────────────────────────────────────
    private void tailSweep(ServerLevel sl, long now) {
        nextSlamTick = now + cd(20 * 11);
        playSound(SoundEvents.PHANTOM_SWOOP, 0.6F, 1.4F);
        double r = 10.0;
        sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(),
                8, r * 0.35, 0.3, r * 0.35, 0);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, injuryNow(getVariant().injury - 10));
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 1.2, 0.9, away.z * 1.2);
            player.hurtMarked = true;
        }
    }

    // ── 漩涡拖拽（克拉肯/利维坦）─────────────────────────────────
    private void vortexPull(ServerLevel sl, long now, LivingEntity target) {
        nextPullTick = now + cd(20 * 14);
        playSound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.5F, 0.8F);
        double r = getVariant() == Variant.LEVIATHAN ? 20.0 : 14.0;
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            Vec3 toward = position().subtract(player.position()).normalize();
            player.setDeltaMovement(player.getDeltaMovement().add(
                    toward.x * 0.15, toward.y * 0.08, toward.z * 0.15));
            player.hurtMarked = true;
        }
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY(), getZ(),
                6, r * 0.3, 0.8, r * 0.3, 0.01);
    }

    // ── 墨云（克拉肯）────────────────────────────────────────────
    private void inkCloud(ServerLevel sl, long now) {
        nextInkTick = now + cd(20 * 20);
        playSound(SoundEvents.SQUID_SQUIRT, 0.6F, 0.6F);
        double r = 8.0;
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(),
                40, r * 0.4, 0.5, r * 0.4, 0.02);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 20 * 4, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0));
        }
    }

    // ── 水炮（海蛇/利维坦远程）──────────────────────────────────
    private void waterBolt(ServerLevel sl, long now, LivingEntity target) {
        nextBoltTick = now + cd(getVariant() == Variant.LEVIATHAN ? 20 * 4 : 20 * 7);
        playSound(SoundEvents.DOLPHIN_SPLASH, 0.5F, 1.6F);
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        SixtySecondsAcidSpitEntity bolt = new SixtySecondsAcidSpitEntity(sl, this);
        double dx = target.getX() - getX();
        double dy = target.getY(0.4) - bolt.getY();
        double dz = target.getZ() - getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        bolt.shoot(dx, dy + horizontal * 0.1, dz, 2.0F, 3.0F);
        bolt.setNoGravity(false);
        sl.addFreshEntity(bolt);
        sl.sendParticles(ParticleTypes.BUBBLE, getX(), getEyeY(), getZ(),
                6, 0.3, 0.2, 0.3, 0.05);
    }

    // ── 毒息（海蛇）───────────────────────────────────────────────
    private void toxicFume(ServerLevel sl, long now) {
        nextInkTick = now + cd(20 * 16);
        playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.3F, 0.5F);
        double r = 11.0;
        sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.0, getZ(),
                30, r * 0.3, 0.6, r * 0.3, 0.01);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            int injury = injuryNow(getVariant().injury - 15);
            SixtySecondsHealthSystem.applyInjury(player, null, Math.max(10, injury));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + 8);
            stats.sync();
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 5, 1));
        }
    }

    // ── 利维坦咆哮 ────────────────────────────────────────────────
    private void leviathanRoar(ServerLevel sl, long now) {
        nextRoarTick = now + cd(20 * 22);
        playSound(SoundEvents.ELDER_GUARDIAN_AMBIENT, 1.0F, 0.6F);
        double r = 22.0;
        sl.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getEyeY(), getZ(),
                5, 1.0, 0.5, 1.0, 0);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20 * 6, 2));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20 * 5, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 6, 0));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.sanity = Math.max(0, stats.sanity - 10);
            stats.sync();
            player.displayClientMessage(Component
                    .translatable("message.noellesroles.ocean.leviathan_roar")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    // ── 召唤小弟（利维坦）─────────────────────────────────────────
    private void summonMinions(ServerLevel sl, long now) {
        nextSummonTick = now + cd(20 * 30);
        playSound(SoundEvents.PUFFER_FISH_BLOW_OUT, 0.5F, 0.4F);
        int count = 4 + sl.random.nextInt(3);
        for (int i = 0; i < count; i++) {
            OceanSharkEntity shark = net.exmo.sre.sixtyseconds.init.ModOceanEntities.OCEAN_SHARK.create(sl);
            if (shark == null) continue;
            double angle = sl.random.nextDouble() * Math.PI * 2;
            double offX = Math.cos(angle) * 5;
            double offZ = Math.sin(angle) * 5;
            shark.moveTo(getX() + offX, getY(), getZ() + offZ, sl.random.nextFloat() * 360.0F, 0.0F);
            OceanSharkEntity.Variant[] variants = {
                    OceanSharkEntity.Variant.TIGER_SHARK, OceanSharkEntity.Variant.HAMMERHEAD
            };
            shark.applyVariant(variants[sl.random.nextInt(variants.length)]);
            shark.setTarget(getTarget());
            sl.addFreshEntity(shark);
        }
    }

    // ── 死亡 ────────────────────────────────────────────────────
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (level() instanceof ServerLevel sl) {
            // 全服播报
            Component msg = Component.translatable("message.noellesroles.ocean.monster_killed",
                            getCustomName() != null ? getCustomName() : Component.literal("???"))
                    .withStyle(ChatFormatting.GOLD);
            sl.getServer().getPlayerList().broadcastSystemMessage(msg, false);
            // 粒子爆发
            sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 2, getZ(),
                    8, 2.0, 1.0, 2.0, 0);
            // 掉落战利品
            dropLoot(sl);
        }
        bossEvent.removeAllPlayers();
    }

    /** 海怪死亡掉落丰富战利品（按变体递增）。 */
    private void dropLoot(ServerLevel sl) {
        Variant v = getVariant();
        int scrapBase = switch (v) {
            case KRAKEN -> 15;
            case SERPENT -> 25;
            case LEVIATHAN -> 40;
        };
        int extraRolls = switch (v) {
            case KRAKEN -> 10;
            case SERPENT -> 18;
            case LEVIATHAN -> 28;
        };
        // 废料
        spawnAtLocation(new net.minecraft.world.item.ItemStack(
                org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP,
                scrapBase + sl.random.nextInt(scrapBase / 2)));
        // 硬币
        spawnAtLocation(new net.minecraft.world.item.ItemStack(
                org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_COIN,
                5 + sl.random.nextInt(11)));
        // 随机稀有物资（按 rolls 掷骰）
        for (int i = 0; i < extraRolls; i++) {
            float r = sl.random.nextFloat();
            if (r < 0.15F) {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(
                        org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_WATER_SMALL, 1));
            } else if (r < 0.30F) {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(
                        org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_CANNED_FOOD, 1));
            } else if (r < 0.45F) {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(
                        org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_BANDAGE, 1));
            } else {
                spawnAtLocation(new net.minecraft.world.item.ItemStack(
                        org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_SCRAP,
                        1 + sl.random.nextInt(3)));
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("OceanEnraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 重载后若还在狂暴血线以内，恢复狂暴血条外观（属性倍率已随存档的 base 值走，不重复叠加）
        if (tag.getBoolean("OceanEnraged")) {
            enraged = true;
            Component name = Component.translatable(getVariant().nameKey())
                    .append(Component.translatable("entity.noellesroles.ocean.enrage_suffix"))
                    .withStyle(ChatFormatting.DARK_RED);
            setCustomName(name);
            bossEvent.setName(name);
            bossEvent.setColor(BossEvent.BossBarColor.RED);
            bossEvent.setOverlay(BossEvent.BossBarOverlay.NOTCHED_6);
        }
    }
}
