package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.BiFunction;

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

    @Override

    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        if (itemStack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
            var writtenContent = itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            var author = writtenContent.author();
            var title = writtenContent.title().raw();
            String shortTitle = title;
            if (shortTitle.length() >= 20)
                shortTitle = shortTitle.substring(0, 18) + "...";
            list.add(Component.translatable("item.noellesroles.newspaper.title", shortTitle)
                    .withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("item.noellesroles.newspaper.author", author)
                    .withStyle(ChatFormatting.GRAY));
        } else if (itemStack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
            list.add(Component.translatable("item.noellesroles.newspaper.lore.draft").withStyle(ChatFormatting.GRAY,
                    ChatFormatting.ITALIC));
        }
    }
}