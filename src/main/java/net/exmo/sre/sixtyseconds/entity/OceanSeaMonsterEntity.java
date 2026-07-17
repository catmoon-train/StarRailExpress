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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // 海怪受伤封顶保护
        float capped = Math.min(amount, getVariant() == Variant.LEVIATHAN ? 120.0F : 80.0F);
        return super.hurt(source, capped);
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
        SixtySecondsHealthSystem.applyInjury(player, null, getVariant().injury);
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

        LivingEntity target = getTarget();
        if (target == null || tickCount % 2 != 0) return;

        long now = serverLevel.getGameTime();
        Variant variant = getVariant();
        double distSqr = distanceToSqr(target);

        switch (variant) {
            case KRAKEN -> tickKraken(serverLevel, target, now, distSqr);
            case SERPENT -> tickSerpent(serverLevel, target, now, distSqr);
            case LEVIATHAN -> tickLeviathan(serverLevel, target, now, distSqr);
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

    // ── 触手扫击（克拉肯/利维坦）────────────────────────────────
    private void tentacleSlam(ServerLevel sl, long now) {
        nextSlamTick = now + 20 * 9;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        double r = getVariant() == Variant.LEVIATHAN ? 10.0 : 7.0;
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1.0, getZ(),
                12, r * 0.4, 0.6, r * 0.4, 0);
        playSound(SoundEvents.ELDER_GUARDIAN_HURT, 0.7F, 0.5F);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, getVariant().injury);
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 0.7, 0.4, away.z * 0.7);
            player.hurtMarked = true;
        }
    }

    // ── 尾部击飞（海蛇）───────────────────────────────────────────
    private void tailSweep(ServerLevel sl, long now) {
        nextSlamTick = now + 20 * 11;
        playSound(SoundEvents.PHANTOM_SWOOP, 0.6F, 1.4F);
        double r = 10.0;
        sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(),
                8, r * 0.35, 0.3, r * 0.35, 0);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            SixtySecondsHealthSystem.applyInjury(player, null, getVariant().injury - 10);
            Vec3 away = player.position().subtract(position()).normalize();
            player.setDeltaMovement(away.x * 1.2, 0.9, away.z * 1.2);
            player.hurtMarked = true;
        }
    }

    // ── 漩涡拖拽（克拉肯/利维坦）─────────────────────────────────
    private void vortexPull(ServerLevel sl, long now, LivingEntity target) {
        nextPullTick = now + 20 * 14;
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
        nextInkTick = now + 20 * 20;
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
        nextBoltTick = now + (getVariant() == Variant.LEVIATHAN ? 20 * 4 : 20 * 7);
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
        nextInkTick = now + 20 * 16;
        playSound(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.3F, 0.5F);
        double r = 11.0;
        sl.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.0, getZ(),
                30, r * 0.3, 0.6, r * 0.3, 0.01);
        for (ServerPlayer player : sl.players()) {
            if (!isValidOceanPrey(player) || distanceToSqr(player) > r * r) continue;
            int injury = getVariant().injury - 15;
            SixtySecondsHealthSystem.applyInjury(player, null, Math.max(10, injury));
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + 8);
            stats.sync();
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 5, 1));
        }
    }

    // ── 利维坦咆哮 ────────────────────────────────────────────────
    private void leviathanRoar(ServerLevel sl, long now) {
        nextRoarTick = now + 20 * 22;
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
        nextSummonTick = now + 20 * 30;
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
