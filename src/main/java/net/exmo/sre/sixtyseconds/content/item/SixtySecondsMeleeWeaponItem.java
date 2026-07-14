package net.exmo.sre.sixtyseconds.content.item;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 60s 近战武器（铁管/钉棒/砍刀…）：左键命中玩家扣除<b>对应健康值</b>（{@link #healthDamage}，
 * 经护甲减伤），走 {@link SixtySecondsHealthSystem} 倒地/处决路径；对怪物用原版攻击属性
 * （注册时 {@code attributes}）。清晨 PvP 禁用期无效。
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
        if (attacker.getAttackStrengthScale(0.5F) < 1f) {
            return false;
        }
        ServerLevel level = attacker.serverLevel();
        if (SixtySecondsHealthSystem.isPvpBlocked(level, attacker, target)) {
            attacker.displayClientMessage(net.minecraft.network.chat.Component
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
