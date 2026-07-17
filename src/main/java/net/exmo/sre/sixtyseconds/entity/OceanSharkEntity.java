package net.exmo.sre.sixtyseconds.entity;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * 海洋鲨鱼：不同体积的鲨鱼变体（礁鲨→中型鲨→大白鲨），带有冲撞、撕咬流血技能。
 * 模型采用盒子模型（鱼形轮廓，纹理逐变体切换）。
 *
 * <h3>变体一览</h3>
 * <table>
 *   <tr><th>变体</th><th>比例</th><th>生命</th><th>伤害</th><th>特殊</th></tr>
 *   <tr><td>REEF_SHARK 礁鲨</td><td>~2.5格</td><td>40</td><td>8</td><td>小巧灵活，移速快</td></tr>
 *   <tr><td>TIGER_SHARK 虎鲨</td><td>~4.5格</td><td>80</td><td>16</td><td>流血撕咬</td></tr>
 *   <tr><td>HAMMERHEAD 锤头鲨</td><td>~5.5格</td><td>100</td><td>18</td><td>冲撞击退</td></tr>
 *   <tr><td>GREAT_WHITE 大白鲨</td><td>~7格</td><td>180</td><td>28</td><td>冲撞+流血，丰富掉落</td></tr>
 *   <tr><td>MEGALODON 巨齿鲨</td><td>~10格</td><td>400</td><td>40</td><td>Boss级，低概率刷新</td></tr>
 * </table>
 */
public class OceanSharkEntity extends OceanCreatureEntity {

    public enum Variant {
        /** 礁鲨：小体型，快速度，低伤害 */
        REEF_SHARK(0, 40.0, 0.35, 8, 2.5F, "ocean_reef_shark"),
        /** 虎鲨：中体型，流血撕咬 */
        TIGER_SHARK(1, 80.0, 0.28, 16, 4.5F, "ocean_tiger_shark"),
        /** 锤头鲨：独特头型，冲撞击退 */
        HAMMERHEAD(2, 100.0, 0.26, 18, 5.5F, "ocean_hammerhead"),
        /** 大白鲨：大体型，高伤冲撞+流血 */
        GREAT_WHITE(3, 180.0, 0.24, 28, 7.0F, "ocean_great_white"),
        /** 巨齿鲨：Boss级，只在高危险海域/指令生成 */
        MEGALODON(4, 400.0, 0.20, 40, 10.0F, "ocean_megalodon");

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
            return REEF_SHARK;
        }

        public String nameKey() {
            return "entity.noellesroles." + textureName;
        }
    }

    // ── 技能冷却 ─────────────────────────────────────────────────
    private int chargeCooldown = 0;
    private int chargeWindup = 0;
    private Vec3 chargeDir = Vec3.ZERO;

    public OceanSharkEntity(EntityType<? extends OceanCreatureEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new OceanSwimGoal(this, 0.6, 30));
        this.goalSelector.addGoal(1, new OceanMeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, ServerPlayer.class,
                10, true, false, p -> isValidOceanPrey((ServerPlayer) p)));
    }

    /** 生成时按变体装配（不经过 SynchedEntityData，给自然刷新/指令直接用）。 */
    public void applyVariant(Variant variant) {
        applyVariant(variant.id, variant.health, variant.speed, variant.scale, variant.nameKey());
    }

    public Variant getVariant() {
        return Variant.byId(getVariantId());
    }

    public ResourceLocation textureLocation() {
        return Noellesroles.id("textures/entity/" + getVariant().textureName + ".png");
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return super.doHurtTarget(target);
        if (!(target instanceof ServerPlayer player) || !isValidOceanPrey(player)) {
            setTarget(null);
            return false;
        }
        player.invulnerableTime = 10;
        playSound(SoundEvents.POLAR_BEAR_WARNING, 0.4F, 0.5F);
        int injury = getVariant().injury;
        // 大白鲨/巨齿鲨额外流血
        if (getVariant() == Variant.GREAT_WHITE || getVariant() == Variant.MEGALODON) {
            SixtySecondsStatsComponent stats = SixtySecondsStatsComponent.KEY.get(player);
            stats.pollution = Math.min(100, stats.pollution + 3);
            stats.sync();
        }
        SixtySecondsHealthSystem.applyInjury(player, null, injury);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel serverLevel) || isRemoved()) return;

        LivingEntity target = getTarget();
        if (chargeWindup > 0) {
            chargeWindup--;
            if (chargeWindup == 0) {
                // 冲撞释放
                chargeDir = target != null ? target.position().subtract(position()).normalize() : getLookAngle();
                setDeltaMovement(chargeDir.x * 1.8, chargeDir.y * 0.3, chargeDir.z * 1.8);
                hurtMarked = true;
                playSound(SoundEvents.POLAR_BEAR_WARNING, 0.6F, 1.2F);
                serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                        getX(), getY() + 0.5, getZ(), 10, 0.8, 0.4, 0.8, 0.05);
            }
            return;
        }

        // 仍在冲刺中（惯性）
        if (getDeltaMovement().length() > 1.0 && target != null && distanceToSqr(target) < 3.0 * 3.0) {
            doHurtTarget(target);
        }

        if (target == null || tickCount % 2 != 0) return;

        // 冲撞技能（锤头鲨/大白鲨/巨齿鲨）
        if (chargeCooldown > 0) {
            chargeCooldown--;
        } else {
            double distSqr = distanceToSqr(target);
            boolean canCharge = switch (getVariant()) {
                case HAMMERHEAD, GREAT_WHITE -> distSqr > 4 * 4 && distSqr < 14 * 14 && hasLineOfSight(target);
                case MEGALODON -> distSqr > 5 * 5 && distSqr < 20 * 20 && hasLineOfSight(target);
                default -> false;
            };
            if (canCharge) {
                int cooldown = getVariant() == Variant.MEGALODON ? 10 : 14;
                chargeCooldown = 20 * cooldown;
                chargeWindup = 15; // 0.75s 前摇
                chargeDir = target.position().subtract(position()).normalize();
                serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        getX(), getY() + 0.5, getZ(), 6, 0.6, 0.3, 0.6, 0);
                playSound(SoundEvents.DOLPHIN_SPLASH, 0.5F, 1.5F);
            }
        }
    }
}
