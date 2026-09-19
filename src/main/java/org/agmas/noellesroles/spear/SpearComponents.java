package org.agmas.noellesroles.spear;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

/**
 * 矛的攻击参数组件（攻击距离 / 挥击动画 / 蓄力移动表现 / 穿刺 / 动能冲锋）。
 * <p>
 * 这些参数以普通 Java 记录的形式存在，装配值集中在 {@link SpearConfig} 中，
 * 与「一把下界合金矛」的设定对应；日后要扩展成多把矛时，只需把这些记录挂到物品上即可。
 */
public final class SpearComponents {

    private SpearComponents() {
    }

    /** 攻击距离：最近 / 最远可命中的前方距离（格）。 */
    public record AttackRange(float minReach, float maxReach) {
    }

    /** 挥击动画：时长（tick）与类型（"stab" 直刺 / "whack" 普通 / "none" 无）。 */
    public record SwingAnimation(int swingTicks, String swingType) {
        public static final String STAB = "stab";
        public static final String NONE = "none";

        public boolean isStab() {
            return STAB.equals(this.swingType);
        }
    }

    /** 举矛蓄力时的移动 / 交互表现。 */
    public record UseEffects(float strafeSpeed, boolean allowSprinting, boolean interactVibrations) {
    }

    /** 穿刺武器：普攻（左键直刺）参数。 */
    public record PiercingWeapon(
            float hitboxMargin,
            boolean dealsKnockback,
            boolean dismounts,
            SoundEvent sound,
            SoundEvent hitSound) {

        /** 判断攻击方能否命中该目标。 */
        public static boolean canHit(Entity attacker, Entity target) {
            if (target == attacker || !target.canBeHitByProjectile() || target.isInvulnerable()
                    || !target.isAlive()) {
                return false;
            }
            // 同一载具上的乘客互不伤害。
            return !attacker.isPassengerOfSameVehicle(target);
        }

        /** 播放挥矛音效（只给玩家播，避免刷屏）。 */
        public void playSound(Entity entity) {
            if (entity instanceof Player player && this.sound != null) {
                player.level().playSound(player, entity.getX(), entity.getY(), entity.getZ(), this.sound,
                        entity.getSoundSource(), 1.0F, 1.0F);
            }
        }

        /** 播放命中音效。 */
        public void playHitSound(Entity entity) {
            if (this.hitSound != null) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), this.hitSound,
                        entity.getSoundSource(), 1.0F, 1.0F);
            }
        }
    }

    /**
     * 动能武器（右键蓄力冲锋）。
     *
     * @param hitboxMargin         命中盒膨胀
     * @param contactCooldownTicks 同一目标的接触冷却（tick）
     * @param delayTicks           蓄力前摇（tick）
     * @param dismountConditions   击落坐骑条件
     * @param knockbackConditions  击退条件
     * @param damageConditions     伤害条件
     * @param forwardMovement      命中后自身前冲量
     * @param damageMultiplier     相对速度转化倍率
     * @param sound                举矛音效
     * @param hitSound             命中音效
     */
    public record KineticWeapon(
            float hitboxMargin,
            int contactCooldownTicks,
            int delayTicks,
            Optional<Condition> dismountConditions,
            Optional<Condition> knockbackConditions,
            Optional<Condition> damageConditions,
            float forwardMovement,
            float damageMultiplier,
            SoundEvent sound,
            SoundEvent hitSound) {

        /** 冲锋条件：在给定时长内、且达到最低速度 / 相对速度才成立。 */
        public record Condition(int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
            public boolean isSatisfied(int durationTicks, double speed, double relativeSpeed,
                    double minSpeedMultiplier) {
                return durationTicks <= this.maxDurationTicks
                        && speed >= this.minSpeed * minSpeedMultiplier
                        && relativeSpeed >= this.minRelativeSpeed * minSpeedMultiplier;
            }

            public static Optional<Condition> ofMinSpeed(int maxDurationTicks, float minSpeed) {
                return Optional.of(new Condition(maxDurationTicks, minSpeed, 0.0F));
            }

            public static Optional<Condition> ofMinRelativeSpeed(int maxDurationTicks, float minRelativeSpeed) {
                return Optional.of(new Condition(maxDurationTicks, 0.0F, minRelativeSpeed));
            }
        }

        /** 总蓄力时长（超过后进入收招）。 */
        public int getUseTicks() {
            return this.delayTicks + this.damageConditions.map(Condition::maxDurationTicks).orElse(0);
        }

        /** 与玩家同向的 20 倍速度（用于冲锋判定）。 */
        public static net.minecraft.world.phys.Vec3 getAmplifiedMovement(Entity entity) {
            Entity owner = entity;
            if (!(owner instanceof Player) && owner.isPassenger()) {
                owner = owner.getRootVehicle();
            }
            // Yarn's PlayerEntity#getMovement used by Backported-Spears maps to
            // Mojmap's getKnownMovement on 1.21.1. Use that value for players;
            // raw delta movement can be stale/zero during server-side movement.
            net.minecraft.world.phys.Vec3 movement = owner instanceof Player player
                    ? player.getKnownMovement()
                    : owner.position().subtract(owner.xo, owner.yo, owner.zo);

            // 乘坐模组坐骑时，玩家本身在部分服务端 tick 中的位移仍可能接近 0，
            // 导致右键冲锋永远达不到原版矛的速度条件。使用坐骑实际位移作为兜底，
            // 保留玩家自身位移较大时的方向/速度数据。
            if (entity instanceof Player player && player.isPassenger()) {
                Entity vehicle = player.getRootVehicle();
                net.minecraft.world.phys.Vec3 vehicleMovement = vehicle.position()
                        .subtract(vehicle.xo, vehicle.yo, vehicle.zo);
                if (vehicleMovement.lengthSqr() > movement.lengthSqr()) {
                    movement = vehicleMovement;
                }
            }
            return movement.scale(20.0D);
        }

        public void playSound(Entity entity) {
            if (entity instanceof Player player && this.sound != null) {
                player.level().playSound(player, entity.getX(), entity.getY(), entity.getZ(), this.sound,
                        entity.getSoundSource(), 1.0F, 1.0F);
            }
        }

        public void playHitSound(Entity entity) {
            if (this.hitSound != null) {
                entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), this.hitSound,
                        entity.getSoundSource(), 1.0F, 1.0F);
            }
        }
    }
}
