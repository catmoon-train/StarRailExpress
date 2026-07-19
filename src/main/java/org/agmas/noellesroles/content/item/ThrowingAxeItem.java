package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.ChargeableItem;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.FlyingAxeEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 飞斧 —— 强盗的蓄力投掷武器。
 *
 * <p>
 * 按住右键蓄力 {@link #CHARGE_TICKS}（0.8 秒），松手投掷一柄 {@link FlyingAxeEntity}：
 * 直线飞行、最多穿透击杀 2 名玩家、撞墙后钉住 5 秒消失。非创造模式下投掷即消耗（物品被移除），
 * 创造模式下不消耗；蓄力不足 0.8 秒松手则不发射。
 */
public class ThrowingAxeItem extends Item implements ChargeableItem, TrainWeapon {

    /** 蓄力所需时长（tick）。20 tick = 1 秒，16 tick = 0.8 秒。 */
    public static final int CHARGE_TICKS = 16;
    /** 投掷冷却（tick）。飞斧可穿透击杀 2 人，冷却略高于左轮（40 tick）。 */
    public static final int COOLDOWN_TICKS = 20 * 3;
    /** 使用时长上限（保持蓄力直到松手）。 */
    private static final int MAX_USE_DURATION = 72000;

    public ThrowingAxeItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) {
        return CHARGE_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 角色门控：与左轮一致，尊重「禁用武器」等修饰（放逐 / 会议等）。
        SRERole role = getRole(world, user);
        if (role != null && !role.onUseGun(user)) {
            return InteractionResultHolder.fail(stack);
        }
        // 冷却中 / 观战 / 安全时间：不允许开始蓄力。
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return InteractionResultHolder.fail(stack);
        }

        // 开始蓄力（松手时在 releaseUsing 里判定是否达到 0.8 秒并投掷）。
        user.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level world, @NotNull LivingEntity entity,
            int timeLeft) {
        if (!(entity instanceof Player user)) {
            return;
        }
        int charged = this.getUseDuration(stack, entity) - timeLeft;
        if (charged < CHARGE_TICKS) {
            return; // 蓄力不足 0.8 秒，不发射
        }

        // 松手时再校验一次门控/冷却/状态。
        SRERole role = getRole(world, user);
        if (role != null && !role.onUseGun(user)) {
            return;
        }
        if (user.getCooldowns().isOnCooldown(this) || user.isSpectator()
                || user.hasEffect(ModEffects.SAFE_TIME)) {
            return;
        }

        if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
            FlyingAxeEntity axe = new FlyingAxeEntity(ModEntities.FLYING_AXE, user, world,
                    ModItems.THROWING_AXE.getDefaultInstance());
            axe.setPos(user.getEyePosition());
            axe.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0f, 2.5f, 1.0f);
            axe.setOwner(user);
            world.addFreshEntity(axe);

            ServerLevel serverLevel = serverPlayer.serverLevel();
            serverLevel.players().forEach(p -> serverLevel.playSound(p, axe.getX(), axe.getY(), axe.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.9f));

            if (!user.isCreative()) {
                user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                stack.shrink(1);
            }
        }

        user.swing(user.getUsedItemHand());
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.SPEAR; // 举斧过顶的蓄力姿态
    }

    /** 客户端 / 服务端分别取角色（与 {@code BanditRevolverItem} 一致）。 */
    private static SRERole getRole(Level world, Player user) {
        if (world.isClientSide) {
            var gameComponent = SREClient.gameComponent;
            return gameComponent == null ? null : gameComponent.getRole(user);
        }
        var gameComponent = SREGameWorldComponent.KEY.get(world);
        return gameComponent == null ? null : gameComponent.getRole(user);
    }
}
