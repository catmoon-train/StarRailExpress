package org.agmas.noellesroles.spear;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.init.NRSounds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 下界合金矛的战斗结算：直刺（穿刺）、右键蓄力冲锋（动能）、突进附魔与伤害转换。
 * <p>
 * 伤害规则：
 * <ul>
 * <li>只有开启了 {@code canUseSpVanillaWeapon} 的职业，才能用矛击杀玩家；</li>
 * <li>矛对<b>玩家</b>造成的原版伤害会转化为虚拟伤害，数值为 {@code 原版伤害 * 2}，
 * 并通过 {@code noellesroles:spear} 死因结算（见 {@link #DEATH_REASON}）；</li>
 * <li>对非玩家实体仍走原版伤害。</li>
 * </ul>
 */
public final class SpearCombat {

    private SpearCombat() {
    }

    /** 矛造成的死因。 */
    public static final ResourceLocation DEATH_REASON = Noellesroles.id("spear");

    /** 玩家伤害 → 虚拟伤害的倍率。 */
    public static final float VIRTUAL_DAMAGE_MULTIPLIER = 2.0F;

    // ───────────────────────── 直刺（穿刺） ─────────────────────────

    /**
     * 左键直刺：对视线前方命中盒内的所有目标各执行一次 {@link #pierce}。
     *
     * @return 是否命中了至少一个目标
     */
    public static boolean stab(LivingEntity attacker, EquipmentSlot slot) {
        ItemStack stack = attacker.getItemBySlot(slot);
        SpearComponents.PiercingWeapon piercing = SpearConfig.piercing();
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hit = false;
        for (EntityHitResult result : collectPiercingCollisions(attacker,
                SpearConfig.ATTACK_RANGE.minReach(),
                SpearConfig.ATTACK_RANGE.maxReach(),
                piercing.hitboxMargin(),
                target -> SpearComponents.PiercingWeapon.canHit(attacker, target))) {
            hit |= pierce(attacker, slot, result.getEntity(), damage, true, piercing.dealsKnockback(),
                    piercing.dismounts());
        }
        // Backported-Spears triggers the post-piercing enchantment effect after
        // every completed stab, even when the collision scan found no target.
        // Keep the lunge outside the hit-only branch for the same behavior.
        applyLunge(attacker, stack);
        if (hit) {
            piercing.playHitSound(attacker);
            if (stack.getMaxDamage() > 0) {
                stack.hurtAndBreak(1, attacker, slot);
            }
        }
        piercing.playSound(attacker);
        attacker.swing(InteractionHand.MAIN_HAND, false);
        return hit;
    }

    // ───────────────────────── 蓄力冲锋（动能） ─────────────────────────

    /** 举矛蓄力时每 tick 调用：达到条件后对前方目标造成击落 / 击退 / 伤害。 */
    public static void usageTick(ItemStack stack, int remainingUseTicks, LivingEntity user, EquipmentSlot slot) {
        SpearComponents.KineticWeapon kinetic = SpearConfig.kinetic();
        int used = stack.getItem().getUseDuration(stack, user) - remainingUseTicks;
        if (used < kinetic.delayTicks() || !(user instanceof SpearUser spearUser)) {
            return;
        }
        // 去掉前摇后才是「有效冲锋时长」（lambda 里要用，必须实际不可变）
        final int chargeTicks = used - kinetic.delayTicks();
        Vec3 look = user.getViewVector(1.0F);
        double forwardSpeed = look.dot(SpearComponents.KineticWeapon.getAmplifiedMovement(user));
        float speedMultiplier = user instanceof Player ? 1.0F : 0.2F;
        float reachMultiplier = user instanceof Player ? 1.0F : 0.5F;
        double attackDamage = user.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boolean hit = false;
        for (EntityHitResult result : collectPiercingCollisions(user,
                reachMultiplier * SpearConfig.ATTACK_RANGE.minReach(),
                reachMultiplier * SpearConfig.ATTACK_RANGE.maxReach(),
                kinetic.hitboxMargin(),
                target -> SpearComponents.PiercingWeapon.canHit(user, target))) {
            Entity entity = result.getEntity();
            boolean cooling = spearUser.isInPiercingCooldown(entity, kinetic.contactCooldownTicks());
            spearUser.startPiercingCooldown(entity);
            if (cooling) {
                continue;
            }
            double relativeSpeed = Math.max(0.0,
                    forwardSpeed - look.dot(SpearComponents.KineticWeapon.getAmplifiedMovement(entity)));
            boolean dismount = kinetic.dismountConditions()
                    .map(c -> c.isSatisfied(chargeTicks, forwardSpeed, relativeSpeed, speedMultiplier)).orElse(false);
            boolean knockback = kinetic.knockbackConditions()
                    .map(c -> c.isSatisfied(chargeTicks, forwardSpeed, relativeSpeed, speedMultiplier)).orElse(false);
            boolean damage = kinetic.damageConditions()
                    .map(c -> c.isSatisfied(chargeTicks, forwardSpeed, relativeSpeed, speedMultiplier)).orElse(false);
            if (dismount || knockback || damage) {
                float hitDamage = (float) attackDamage + Mth.floor(relativeSpeed * kinetic.damageMultiplier());
                hit |= spearUser.pierce(slot, entity, hitDamage, damage, knockback, dismount);
            }
        }
        if (hit) {
            kinetic.playHitSound(user);
            // 广播一次受击动作，供客户端计算冲锋收招动画
            user.level().broadcastEntityEvent(user, (byte) 2);
        }
    }

    // ───────────────────────── 单次命中结算 ─────────────────────────

    /** 对单个目标执行伤害 / 击退 / 击落坐骑。 */
    public static boolean pierce(LivingEntity attacker, EquipmentSlot slot, Entity target, float damage,
            boolean dealDamage, boolean knockback, boolean dismount) {
        if (!(attacker.level() instanceof ServerLevel) || target == null || !target.isAlive()) {
            return false;
        }
        // 只有开启 canUseSpVanillaWeapon 的职业能用矛对付其他玩家
        if (attacker instanceof ServerPlayer sp && target instanceof ServerPlayer && !canKillWithSpear(sp)) {
            return false;
        }
        boolean dismounted = false;
        if (dismount && target.isPassenger()) {
            target.stopRiding();
            dismounted = true;
        }
        if (knockback && target instanceof LivingEntity knockable) {
            knockable.knockback(0.4D, attacker.getX() - target.getX(), attacker.getZ() - target.getZ());
        }

        boolean hurt = false;
        if (attacker instanceof ServerPlayer player) {
            if (target instanceof ServerPlayer victim) {
                // 玩家目标：原版伤害 ×2 转为虚拟伤害，由 spear 死因结算
                if (dealDamage && canKillWithSpear(player)) {
                    int virtualDamage = Math.max(1, Math.round(damage * VIRTUAL_DAMAGE_MULTIPLIER));
                    hurt = DreamHealthComponent.KEY.get(victim).hurt(player, virtualDamage, DEATH_REASON);
                    if (hurt) {
                        player.causeFoodExhaustion(0.1F);
                        if (GameUtils.isPlayerAliveAndSurvival(victim)) {
                            // 只给一次轻微原版伤害用于受击动画与音效，不参与击杀判定
                            victim.invulnerableTime = 0;
                            victim.hurt(victim.damageSources().playerAttack(player), 1.0F);
                        }
                    }
                }
            } else if (dealDamage) {
                hurt = target.hurt(attacker.damageSources().playerAttack(player), damage);
                if (hurt) {
                    player.causeFoodExhaustion(0.1F);
                }
            }
            return hurt || knockback || dismounted;
        }
        if (dealDamage) {
            DamageSource source = attacker.damageSources().mobAttack(attacker);
            hurt = target.hurt(source, damage);
        }
        return hurt || knockback || dismounted;
    }

    /** 持矛者的职业是否允许用矛削减他人虚拟血量并击杀。 */
    public static boolean canKillWithSpear(ServerPlayer attacker) {
        var gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        var role = gameWorld == null ? null : gameWorld.getRole(attacker);
        return role != null && role.canUseSpVanillaWeapon();
    }

    // ───────────────────────── 客户端直刺请求 ─────────────────────────

    /** 服务端收到客户端直刺包后的处理：校验主手持矛后执行一次穿刺。 */
    public static void handleStab(ServerPlayer player, int entityId) {
        if (player == null || player.level().getEntity(entityId) != player) {
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!SpearConfig.isSpear(mainHand)) {
            return;
        }
        if (mainHand.getMaxDamage() > 0 && mainHand.getDamageValue() >= mainHand.getMaxDamage()) {
            return;
        }
        stab(player, EquipmentSlot.MAINHAND);
        player.resetAttackStrengthTicker();
    }

    // ───────────────────────── 突进（Lunge）附魔 ─────────────────────────

    /** 物品上「突进」附魔的等级（没有则 0）。 */
    public static int lungeLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        for (var entry : stack.getEnchantments().entrySet()) {
            String id = entry.getKey().unwrapKey().map(key -> key.location().toString()).orElse("");
            if (id.endsWith(":" + SpearConfig.LUNGE_ENCHANTMENT_PATH)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    /**
     * 持矛命中后触发「突进」：沿视线水平方向给自己一个冲刺，消耗饥饿并播放音效。
     * 骑乘、鞘翅飞行、水中时不生效（与附魔定义一致）。
     */
    public static void applyLunge(LivingEntity attacker, ItemStack stack) {
        if (!(attacker instanceof Player player)) {
            return;
        }
        int level = lungeLevel(stack);
        if (level <= 0 || player.isPassenger() || player.isFallFlying() || player.isInWater()) {
            return;
        }
        Vec3 look = player.getViewVector(1.0F);
        double magnitude = SpearConfig.LUNGE_IMPULSE_PER_LEVEL * level;
        player.push(look.x * magnitude, 0.0D, look.z * magnitude);
        // Backported-Spears sets both velocityModified and velocityDirty after
        // applying the impulse. In Mojmap 1.21.1, hurtMarked is the flag that
        // makes ServerEntity resend the player's changed motion to clients.
        player.hurtMarked = true;
        player.causeFoodExhaustion(SpearConfig.LUNGE_EXHAUSTION_PER_LEVEL * level);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), NRSounds.SPEAR_LUNGE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // ───────────────────────── 视线内的多个目标收集 ─────────────────────────

    /** 收集视线前方（{@code minReach} ~ {@code maxReach}）可被击中的所有实体，可穿透多个目标。 */
    public static Collection<EntityHitResult> collectPiercingCollisions(LivingEntity entity, float minReach,
            float maxReach, float hitboxMargin, Predicate<Entity> hitPredicate) {
        Vec3 look = entity.getViewVector(1.0F);
        Vec3 eye = entity.getEyePosition();
        Vec3 from = eye.add(look.scale(minReach));
        // A passenger's own delta movement can be zero while the mount is moving.
        // Keep the collision segment consistent with the mounted-aware speed test.
        double forward = SpearComponents.KineticWeapon.getAmplifiedMovement(entity)
                .scale(1.0D / 20.0D).dot(look);
        Vec3 to = eye.add(look.scale(maxReach + Math.max(0.0, forward)));
        return collectPiercingCollisions(entity, eye, from, hitPredicate, to, hitboxMargin);
    }

    private static Collection<EntityHitResult> collectPiercingCollisions(Entity entity, Vec3 eye, Vec3 from,
            Predicate<Entity> hitPredicate, Vec3 to, float hitboxMargin) {
        Level level = entity.level();
        BlockHitResult blockHit = level.clip(
                new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
            if (eye.distanceToSqr(to) < eye.distanceToSqr(from)) {
                return List.of();
            }
        }
        Vec3 delta = to.subtract(from);
        AABB box = new AABB(from.x - hitboxMargin, from.y - hitboxMargin, from.z - hitboxMargin,
                from.x + hitboxMargin, from.y + hitboxMargin, from.z + hitboxMargin)
                .expandTowards(delta.x, delta.y, delta.z)
                .inflate(1.0D);
        List<EntityHitResult> results = new ArrayList<>();
        for (Entity target : level.getEntities(entity, box, hitPredicate)) {
            AABB targetBox = target.getBoundingBox();
            if (targetBox.contains(from)) {
                results.add(new EntityHitResult(target, from));
                continue;
            }
            Optional<Vec3> clip = targetBox.clip(from, to);
            if (clip.isPresent()) {
                results.add(new EntityHitResult(target, clip.get()));
                continue;
            }
            if (hitboxMargin > 0.0F) {
                Optional<Vec3> rough = targetBox.inflate(hitboxMargin).clip(from, to);
                if (rough.isPresent()) {
                    Vec3 point = rough.get();
                    Vec3 center = targetBox.getCenter();
                    BlockHitResult inner = level.clip(
                            new ClipContext(point, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                                    entity));
                    if (inner.getType() != HitResult.Type.MISS) {
                        center = inner.getLocation();
                    }
                    targetBox.clip(point, center).ifPresent(v -> results.add(new EntityHitResult(target, v)));
                }
            }
        }
        return results;
    }
}
