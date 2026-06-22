package org.agmas.noellesroles.content.item;

import java.util.function.BiFunction;

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
 * 注意：实际的使用逻辑在客户端的 DeliveryBoxItemClient 中通过 Mixin 实现
 */
public class NewspaperItem extends Item {
    public static BiFunction<ItemStack, InteractionHand, Boolean> runner = null;

    public NewspaperItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide) {
            if (runner != null) {
                return runner.apply(stack, hand) ? InteractionResultHolder.success(stack)
                        : InteractionResultHolder.fail(stack);
            }
        }
        return InteractionResultHolder.success(stack);
    }
}