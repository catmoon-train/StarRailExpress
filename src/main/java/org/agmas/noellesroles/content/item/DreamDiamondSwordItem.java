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
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import io.wifi.starrailexpress.game.GameConstants;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

import java.util.List;

/**
 * Dream 的钻石剑（继承原版 {@link SwordItem}，材质复用原版钻石剑）。
 *
 * <p>攻击方式与伤害数值<b>完全复刻原版钻石剑</b>（蓄力缩放、跳劈暴击 1.5x、横扫），
 * 但伤害最终扣除的是<b>虚拟血量</b>（{@link DreamHealthComponent}），不动原版血量。
 * 持有者职业需开启 {@code canUseSpVanillaWeapon} 才能造成伤害。
 *
 * <p>与原版一致的逻辑：
 * <ul>
 * <li>伤害取自玩家 ATTACK_DAMAGE 属性（含武器修饰，钻石剑 7 点），按蓄力
 * {@code 0.2 + t^2 * 0.8} 缩放。</li>
 * <li>暴击：蓄满 + 下落中 + 不在地面/水中/攀爬/失明/骑乘/疾跑 → 1.5 倍。</li>
 * <li>横扫：蓄满 + 非暴击 + 非疾跑 + 在地面 + 位移小 → 对周围玩家各造成 1 点
 * 虚拟伤害（原版无横扫附魔时横扫伤害即 1）。</li>
 * <li>每次命中消耗 1 点耐久。</li>
 * </ul>
 */
public class DreamDiamondSwordItem extends SwordItem implements SREItemProperties.LeftClickHurtable, TrainWeapon {
    /** 虚拟血量归零时的死因（用于死亡播报与击杀归属）。 */
    public static final ResourceLocation DEATH_REASON = GameConstants.DeathReasons.DREAM_DIAMOND_SWORD;

    /**
     * 耐久 12、其余属性委托给钻石的 Tier。
     * <p>
     * 原版 {@code TieredItem} 构造时会用 {@code tier.getUses()} 无条件覆盖
     * Properties 里设置的耐久（钻石为 1561），因此必须在 Tier 层面改掉 uses，
     * 注册处的 {@code .durability(12)} 才能生效。
     */
    private static final net.minecraft.world.item.Tier LOW_DURABILITY_DIAMOND = new net.minecraft.world.item.Tier() {
        @Override
        public int getUses() {
            return 12;
        }

        @Override
        public float getSpeed() {
            return Tiers.DIAMOND.getSpeed();
        }

        @Override
        public float getAttackDamageBonus() {
            return Tiers.DIAMOND.getAttackDamageBonus();
        }

        @Override
        public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
            return Tiers.DIAMOND.getIncorrectBlocksForDrops();
        }

        @Override
        public int getEnchantmentValue() {
            return Tiers.DIAMOND.getEnchantmentValue();
        }

        @Override
        public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() {
            return Tiers.DIAMOND.getRepairIngredient();
        }
    };

    public DreamDiamondSwordItem(Properties properties) {
        super(LOW_DURABILITY_DIAMOND, properties);
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
                    .translatable("item.noellesroles.dream_diamond_sword.no_durability")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        // ── 原版伤害公式：属性伤害 × 蓄力缩放 ──
        float damage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float charge = attacker.getAttackStrengthScale(0.5F);
        damage *= 0.2F + charge * charge * 0.8F;
        attacker.resetAttackStrengthTicker();
        if (damage <= 0.0F) {
            return false;
        }

        boolean fullCharge = charge > 0.9F;
        // 原版暴击条件：蓄满 + 下落中 + 不在地面/攀爬/水中/失明/骑乘/疾跑
        boolean crit = fullCharge && attacker.fallDistance > 0.0F && !attacker.onGround()
                && !attacker.onClimbable() && !attacker.isInWater()
                && !attacker.hasEffect(MobEffects.BLINDNESS) && !attacker.isPassenger()
                && !attacker.isSprinting();
        if (crit) {
            damage *= 1.5F;
        }
        // 原版横扫条件：蓄满 + 非暴击 + 非疾跑 + 在地面 + 本 tick 位移小于移动速度
        boolean sweep = fullCharge && !crit && !attacker.isSprinting() && attacker.onGround()
                && (attacker.walkDist - attacker.walkDistO) < attacker.getSpeed();

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

        if (attacker.level() instanceof ServerLevel serverLevel) {
            // 横扫：对周围玩家各造成 1 点虚拟伤害（原版无附魔横扫伤害 = 1 + 0×damage）+ 轻击退
            if (sweep) {
                for (ServerPlayer nearby : serverLevel.players()) {
                    if (nearby == attacker || nearby == target
                            || !GameUtils.isPlayerAliveAndSurvival(nearby)) {
                        continue;
                    }
                    // 原版判定：目标包围盒外扩 1 格内 且 与攻击者距离平方 < 9
                    if (!target.getBoundingBox().inflate(1.0D, 0.25D, 1.0D).intersects(nearby.getBoundingBox())
                            || attacker.distanceToSqr(nearby) >= 9.0D) {
                        continue;
                    }
                    if (DreamHealthComponent.KEY.get(nearby).hurt(attacker, 1, DEATH_REASON)
                            && GameUtils.isPlayerAliveAndSurvival(nearby)) {
                        nearby.knockback(0.4F,
                                Math.sin(attacker.getYRot() * (Math.PI / 180.0D)),
                                -Math.cos(attacker.getYRot() * (Math.PI / 180.0D)));
                    }
                }
                attacker.sweepAttack();
                serverLevel.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            } else if (crit) {
                serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY(0.5D), target.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.2D);
                serverLevel.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            } else {
                serverLevel.playSound(null, target.blockPosition(),
                        fullCharge ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.dream_diamond_sword.tooltip")
                .withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.noellesroles.dream_diamond_sword.tooltip.durability",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
