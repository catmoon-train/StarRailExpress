package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 传递盒物品
 *
 * 功能：
 * - 射命丸文专属物品，在商店以350金币购买
 * - 指针对准玩家并右键使用，打开传递界面
 * - 双方可以放入一样物品并交换
 *
 * 注意：实际的使用逻辑在客户端的 ClientItemHelper 中通过反射调用
 */
public class DeliveryBoxItem extends Item {
    
    public DeliveryBoxItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 检查物品冷却（3秒 = 60 ticks）
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (world.isClientSide) {
            handleClientUse(user, stack);
            // 客户端逻辑已通过反射处理，返回侧边成功
            // （实际 packet 发送后服务端会处理界面打开）
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        // 服务端：应用3秒冷却
        user.getCooldowns().addCooldown(this, 60);
        return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide());
    }

    /** 通过反射调用客户端逻辑 */
    private static void handleClientUse(Player user, ItemStack stack) {
        try {
            Class<?> helper = Class.forName(
                    "org.agmas.noellesroles.client.utils.ClientItemHelper");
            helper.getMethod("handleDeliveryBoxUse", Player.class, ItemStack.class)
                    .invoke(null, user, stack);
        } catch (Exception e) {
            // 服务端 —— 静默忽略
        }
    }
    
    @Override
    public boolean isFoil(ItemStack stack) {
        // 不添加附魔光效
        return false;
    }
}