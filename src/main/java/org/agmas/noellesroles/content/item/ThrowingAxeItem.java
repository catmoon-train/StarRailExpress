package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.ThrowingAxeEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;

/**
 * 飞斧 —— 强盗的可投掷武器。
 *
 * <p>右键投掷一柄 {@link ThrowingAxeEntity}：直线飞行、最多穿透击杀 2 名玩家、
 * 撞墙后钉住 5 秒消失。武器本身留在手里（像左轮一样可反复使用），投掷后进入
 * {@link #COOLDOWN_TICKS} 冷却。
 *
 * <p>投掷完全在服务端 {@link #use} 里结算（右键会自动把 use 事件发到服务端），
 * 因此无需额外网络包；冷却通过 {@link net.minecraft.world.item.ItemCooldowns} 自动同步到客户端。
 */
public class ThrowingAxeItem extends Item {

    /** 投掷冷却（tick）。20 tick = 1 秒。飞斧可穿透击杀 2 人，冷却略高于左轮（40 tick）。 */
    public static final int COOLDOWN_TICKS = 20 * 3;

    public ThrowingAxeItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 角色门控：与左轮一致，尊重「禁用武器」等修饰（放逐 / 会议等）。
        SRERole role = getRole(world, user);
        if (role != null && !role.onUseGun(user)) {
            return InteractionResultHolder.fail(stack);
        }

        // 冷却中 / 观战 / 安全时间：不允许投掷。
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (user.isSpectator() || user.hasEffect(ModEffects.SAFE_TIME)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!world.isClientSide && user instanceof ServerPlayer serverPlayer) {
            ThrowingAxeEntity axe = new ThrowingAxeEntity(ModEntities.THROWING_AXE, user, world,
                    ModItems.THROWING_AXE.getDefaultInstance());
            axe.setPos(user.getEyePosition());
            axe.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0f, 2.5f, 1.0f);
            axe.setOwner(user);
            world.addFreshEntity(axe);

            ServerLevel serverLevel = serverPlayer.serverLevel();
            serverLevel.players().forEach(p -> serverLevel.playSound(p, axe.getX(), axe.getY(), axe.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 1.0f));

            if (!user.isCreative()) {
                user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            }
        }

        user.swing(hand);
        return InteractionResultHolder.consume(stack);
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
