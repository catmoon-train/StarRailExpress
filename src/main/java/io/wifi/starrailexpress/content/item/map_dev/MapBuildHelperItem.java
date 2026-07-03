package io.wifi.starrailexpress.content.item.map_dev;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Predicate;

public class MapBuildHelperItem extends Item {

    public static Predicate<Player> openScreenCallback = null;

    public MapBuildHelperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 客户端：打开GUI
        if (world.isClientSide()) {
            if (openScreenCallback != null) {
                if (openScreenCallback.test(user)) {
                    return InteractionResultHolder.success(stack);
                }
            }
        }
        // 返回 success 但不消耗物品，等猜测完成后再消耗
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, List<Component> list,
            TooltipFlag tooltipFlag) {
        list.add(Component.translatable(getDescriptionId() + ".tooltip"));
    }
}
