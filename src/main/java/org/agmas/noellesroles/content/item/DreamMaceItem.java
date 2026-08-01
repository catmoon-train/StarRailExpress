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

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

import java.util.List;

/**
 * Dream 的重锤（继承原版 {@link MaceItem}，材质复用原版重锤）。
 *
 * <p>攻击方式与伤害数值<b>完全复刻原版重锤</b>（蓄力缩放、下落高度加伤、
 * 坠击范围击退、坠击免摔落伤害），但伤害最终扣除的是<b>虚拟血量</b>
 * （{@link DreamHealthComponent}），不动原版血量。
 * 持有者职业需开启 {@code canUseSpVanillaWeapon} 才能造成伤害。
 *
 * <p>与原版一致的逻辑：
 * <ul>
 * <li>基础伤害取自玩家 ATTACK_DAMAGE 属性（含武器修饰，重锤 6 点），按蓄力
 * {@code 0.2 + t^2 * 0.8} 缩放。</li>
 * <li>坠击加伤（下落高度 &gt; 1.5 格且未鞘翅飞行时触发，不受蓄力缩放）：
 * 前 3 格每格 +4，3~8 格每格 +2，超过 8 格每格 +1。</li>
 * <li>坠击命中：清空攻击者摔落高度（免摔落伤害）、播放砸地音效、
 * 击退目标周围 3.5 格内的其他玩家。</li>
 * <li>非坠击时同样支持原版暴击（1.5 倍）。每次命中消耗 1 点耐久。</li>
 * </ul>
 */
public class DreamMaceItem extends MaceItem implements SREItemProperties.LeftClickHurtable, TrainWeapon {
    /** 虚拟血量归零时的死因（用于死亡播报与击杀归属）。 */
    public static final ResourceLocation DEATH_REASON = GameConstants.DeathReasons.DREAM_MACE;
    /** 原版坠击击退半径（格）。 */
    private static final double SMASH_KNOCKBACK_RADIUS = 3.5D;
    /** 原版坠击击退强度系数。 */
    private static final float SMASH_KNOCKBACK_POWER = 0.7F;

    public DreamMaceItem(Properties properties) {
        super(properties);
    }

    /** 原版坠击触发条件：下落高度 > 1.5 格且未鞘翅飞行。 */
    private static boolean canSmash(ServerPlayer attacker) {
        return attacker.fallDistance > 1.5F && !attacker.isFallFlying();
    }

    /**
     * 原版重锤下落加伤公式：前 3 格每格 +4，3~8 格每格 +2，之后每格 +1。
     */
    private static float fallBonusDamage(float fallDistance) {
        if (fallDistance <= 3.0F) {
            return 4.0F * fallDistance;
        }
        if (fallDistance <= 8.0F) {
            return 12.0F + 2.0F * (fallDistance - 3.0F);
        }
        return 22.0F + fallDistance - 8.0F;
    }

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        // 持有者职业需开启 canUseSpVanillaWeapon（由 SRERole 通用方法决定，与具体职业解耦）
        if (!gameWorld.getRole(attacker).canUseSpVanillaWeapon()) {
            return false;
        }
        // 耐久耗尽保护（正常情况下 hurtAndBreak 会直接打碎）
        if (mainhandItem.getMaxDamage() > 0 && mainhandItem.getDamageValue() >= mainhandItem.getMaxDamage()) {
            attacker.displayClientMessage(Component
                    .translatable("item.noellesroles.dream_mace.no_durability")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // ── 原版伤害公式：属性伤害 × 蓄力缩放 + 坠击加伤（加伤不受蓄力缩放） ──
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = attacker.getAttackStrengthScale(0.5F);
        damage *= 0.2F + charge * charge * 0.8F;
        attacker.resetAttackStrengthTicker();

        boolean fullCharge = charge > 0.9F;
        boolean smash = canSmash(attacker);
        if (smash) {
            damage += fallBonusDamage(attacker.fallDistance);
        } else {
            // 非坠击时按原版判定暴击（坠击与暴击不叠加，与原版一致由坠击优先）
            boolean crit = fullCharge && attacker.fallDistance > 0.0F && !attacker.onGround()
                    && !attacker.onClimbable() && !attacker.isInWater()
                    && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger()
                    && !attacker.isSprinting();
            if (crit) {
                damage *= 1.5F;
            }
        }
        if (damage <= 0.0F) {
            return false;
        }

        // ── 扣虚拟血量（四舍五入，至少 1 点） ──
        int virtualDamage = Math.max(1, Math.round(damage));
        if (!DreamHealthComponent.KEY.get(target).hurt(attacker, virtualDamage, DEATH_REASON)) {
            return false;
        }

        mainhandItem.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);

        // 受击反馈：红屏动画 + 音效 + 原版击退（1 点伤害本身不参与击杀判定）
        if (GameUtils.isPlayerAliveAndSurvival(target)) {
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().playerAttack(attacker), 1.0F);
        }

        if (smash) {
            // 原版坠击落地效果：免摔落伤害 + 砸地音效 + 范围击退
            attacker.resetFallDistance();
            if (attacker.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.MACE_SMASH_GROUND,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                knockbackNearby(serverLevel, attacker, target);
            }
        } else {
            attacker.level().playSound(null, target.blockPosition(),
                    fullCharge ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return false;
    }

    /**
     * 原版坠击范围击退：目标 3.5 格内的其他玩家被向外推开，
     * 强度随距离线性衰减（{@code 0.7 × (1 - d/3.5)}），并带 0.7 的向上分量。
     */
    private static void knockbackNearby(ServerLevel level, ServerPlayer attacker, ServerPlayer target) {
        for (ServerPlayer nearby : level.players()) {
            if (nearby == attacker || nearby == target
                    || !GameUtils.isPlayerAliveAndSurvival(nearby)) {
                continue;
            }
            double distance = nearby.distanceTo(target);
            if (distance > SMASH_KNOCKBACK_RADIUS) {
                continue;
            }
            double dx = nearby.getX() - target.getX();
            double dz = nearby.getZ() - target.getZ();
            double horizontal = Math.max(Math.sqrt(dx * dx + dz * dz), 1.0E-4D);
            float power = SMASH_KNOCKBACK_POWER
                    * (float) ((SMASH_KNOCKBACK_RADIUS - distance) / SMASH_KNOCKBACK_RADIUS);
            Vec3 push = new Vec3(dx / horizontal * power, SMASH_KNOCKBACK_POWER, dz / horizontal * power);
            nearby.push(push.x, push.y, push.z);
            nearby.hurtMarked = true; // 强制同步速度到客户端
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.dream_mace.tooltip")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.noellesroles.dream_mace.tooltip.durability",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
