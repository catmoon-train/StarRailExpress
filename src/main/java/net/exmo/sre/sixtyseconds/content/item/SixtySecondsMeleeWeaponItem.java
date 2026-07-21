package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 60s 近战武器（铁管/钉棒/砍刀…）：左键命中玩家扣除<b>对应健康值</b>（{@link #healthDamage}，
 * 经护甲减伤），走 {@link SixtySecondsHealthSystem} 倒地/处决路径；对怪物用原版攻击属性
 * （注册时 {@code attributes}）。清晨 PvP 禁用期无效。
 * <p>面板"攻击伤害"显示值 ≈ {@link #healthDamage} / {@link SixtySecondsBalance#HEALTH_PER_VANILLA_HP}，
 * 让玩家直观看出"扣多少健康"（150 健康 ≈ 50 原版HP，3:1 深度绑定）；tooltip 另显示真实健康伤害值。
 */
public class SixtySecondsMeleeWeaponItem extends Item implements SREItemProperties.LeftClickHurtable {
    private final int healthDamage;
    /** 命中附加缓慢 IV 的时长（tick，0=无；电击棍用）。 */
    private final int stunTicks;

    public SixtySecondsMeleeWeaponItem(Properties properties, int healthDamage) {
        this(properties, healthDamage, 0);
    }

    public SixtySecondsMeleeWeaponItem(Properties properties, int healthDamage, int stunTicks) {
        super(properties);
        this.healthDamage = healthDamage;
        this.stunTicks = stunTicks;
    }

    public int healthDamage() {
        return healthDamage;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // 60s 模式下，tooltip 显示真实健康伤害（玩家对玩家 PvP 后还会被 ×0.5，但面板上看到的"攻击伤害"
        // 是基线值，便于心算 ×3 得健康伤害）。展示真实健康伤害 + PvP 后实际扣减值，避免玩家误判。
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.health_damage",
                Integer.toString(healthDamage)).withStyle(ChatFormatting.RED));
        int pvpActual = Math.max(1, (int) Math.round(healthDamage * SixtySecondsBalance.PVP_DAMAGE_MULT));
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds.pvp_damage",
                Integer.toString(pvpActual)).withStyle(ChatFormatting.DARK_RED));
    }

    /** 原版攻击链（打怪物 / 非本模式打玩家）命中后损耗耐久，同 SwordItem。 */
    @Override
    public void postHurtEnemy(ItemStack stack, net.minecraft.world.entity.LivingEntity target,
            net.minecraft.world.entity.LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (!SixtySecondsMod.isActive(attacker.level())) {
            return true; // 非本模式走原版
        }
        // 攻击冷却：防连点
        if (SixtySecondsHealthSystem.checkAttackCooldown(attacker)) {
            return false;
        }
        if (attacker.getAttackStrengthScale(0.5F) < 1f) {
            return false;
        }
        ServerLevel level = attacker.serverLevel();
        if (SixtySecondsHealthSystem.isPvpBlocked(level, attacker, target)) {
            attacker.displayClientMessage(Component
                    .translatable("message.noellesroles.sixty_seconds.pvp_blocked"), true);
            return false;
        }
        SixtySecondsHealthSystem.applyInjury(target, attacker, healthDamage);
        // 本路径取消了原版攻击链（postHurtEnemy 不会触发），耐久在此手动损耗
        mainhandItem.hurtAndBreak(1, attacker, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (stunTicks > 0) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, stunTicks, 3, false, false, false));
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.8F, 0.9F);
        attacker.resetAttackStrengthTicker();
        return false; // 取消原版攻击链
    }
}
