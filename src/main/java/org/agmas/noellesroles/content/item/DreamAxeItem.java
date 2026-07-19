package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;
import org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

/**
 * Dream（梦魇）的铁斧。
 *
 * <ul>
 * <li>12 点耐久，每次命中消耗 1 点；第二次购买半价（见商店注册）。</li>
 * <li>左键攻击：<b>蓄力条（原版攻击充能）满才能造成伤害</b>；扣目标的虚拟血量
 * （{@link DreamHealthComponent}）：平A 9 点、跳劈 12 点；狂暴时平A 12 点、跳劈 20 点。</li>
 * <li>命中眩晕 1s（狂暴 2s）：无法移动/技能/物品/背包 + 视野受限（狂暴时 1 级）。</li>
 * <li>右键：原地跳跃，0.2s 冷却。</li>
 * </ul>
 */
public class DreamAxeItem extends Item implements SREItemProperties.LeftClickHurtable, TrainWeapon {
    /** 右键跳跃冷却（0.2s）。 */
    private static final int JUMP_COOLDOWN_TICKS = 4;
    /** 离地超过 3 格时跳跃失败的惩罚冷却（1.5s）。 */
    private static final int JUMP_PENALTY_COOLDOWN_TICKS = 30;
    /** 跳跃允许的最大离地高度（格）。 */
    private static final double MAX_JUMP_GROUND_DISTANCE = 3.0d;

    public DreamAxeItem(Properties properties) {
        super(properties);
    }

    // ── 左键攻击 ────────────────────────────────────────────────

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        if (!GameUtils.isPlayerAliveAndSurvival(attacker) || !GameUtils.isPlayerAliveAndSurvival(target)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(attacker.level());
        if (!gameWorld.isRole(attacker, ModRoles.DREAM)) {
            return false;
        }
        // 蓄力条必须满才能攻击
        if (attacker.getAttackStrengthScale(0.5F) < 1f) {
            return false;
        }
        // 耐久耗尽保护（正常情况下 hurtAndBreak 会直接打碎）
        if (mainhandItem.getMaxDamage() > 0 && mainhandItem.getDamageValue() >= mainhandItem.getMaxDamage()) {
            attacker.displayClientMessage(Component
                    .translatable("item.noellesroles.dream_axe.no_durability")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        var config = NoellesRolesConfig.HANDLER.instance();
        boolean berserk = DreamPlayerComponent.isBerserk(attacker);
        // 跳劈：与原版暴击一致 —— 下落中且不在地面
        boolean crit = attacker.fallDistance > 0.0F && !attacker.onGround() && !attacker.isPassenger();

        int damage;
        if (berserk) {
            damage = crit ? config.dreamAxeBerserkCritDamage : config.dreamAxeBerserkDamage;
        } else {
            damage = crit ? config.dreamAxeCritDamage : config.dreamAxeDamage;
        }

        if (!DreamHealthComponent.KEY.get(target).hurt(attacker, damage,
                DreamPlayerComponent.DEATH_REASON_DREAM_AXE)) {
            return false;
        }

        attacker.resetAttackStrengthTicker();
        mainhandItem.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);

        // 存活则受伤反馈 + 击退 + 眩晕：1s / 狂暴 2s（视野受限：普通 0 级，狂暴 1 级）
        if (GameUtils.isPlayerAliveAndSurvival(target)) {
            // 原版轻微伤害：红屏受击动画 + 受伤音效 + 自带击退（伤害本身不参与击杀判定）
            target.invulnerableTime = 0;
            target.hurt(target.damageSources().playerAttack(attacker), 1.0F);
            double stunSeconds = berserk ? config.dreamBerserkStunSeconds : config.dreamAxeStunSeconds;
            DreamPlayerComponent.applyStun(target, (int) Math.round(stunSeconds * 20), berserk ? 1 : 0);
        }

        attacker.level().playSound(null, target.blockPosition(),
                crit ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0f, crit ? 0.9f : 1.0f);
        return false;
    }

    // ── 右键：原地跳跃 ──────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this) || player.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        // 离地面超过 3 格不能跳，且进入 1.5s 惩罚冷却（两端同判，冷却以服务端为准同步）
        if (!isNearGround(level, player)) {
            player.getCooldowns().addCooldown(this, JUMP_PENALTY_COOLDOWN_TICKS);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            // 跳跃是客户端权威移动，本地直接施加向上速度
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, Math.max(movement.y, 0.42d), movement.z);
        }
        player.getCooldowns().addCooldown(this, JUMP_COOLDOWN_TICKS);
        player.playSound(SoundEvents.HORSE_JUMP, 0.4f, 1.6f);
        return InteractionResultHolder.consume(stack);
    }

    /** 脚下 3 格内是否有可碰撞方块（跳跃的离地高度限制）。 */
    private static boolean isNearGround(Level level, Player player) {
        if (player.onGround()) {
            return true;
        }
        Vec3 from = player.position();
        Vec3 to = from.subtract(0.0d, MAX_JUMP_GROUND_DISTANCE, 0.0d);
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player);
        return level.clip(context).getType() != HitResult.Type.MISS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        var config = NoellesRolesConfig.HANDLER.instance();
        tooltip.add(Component.translatable("item.noellesroles.dream_axe.tooltip.attack",
                config.dreamAxeDamage, config.dreamAxeCritDamage).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("item.noellesroles.dream_axe.tooltip.jump")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.noellesroles.dream_axe.tooltip.durability",
                Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
