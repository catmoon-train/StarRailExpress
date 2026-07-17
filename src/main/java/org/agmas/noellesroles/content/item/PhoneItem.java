package org.agmas.noellesroles.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 电话 - 右键打开拨号页面，拨打热线号码
 *
 * 注意：客户端屏幕通过反射打开，避免服务端加载 client-only 类导致崩溃。
 */
public class PhoneItem extends Item {

    public PhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openPhoneScreen(stack, hand);
        }
        return InteractionResultHolder.success(stack);
    }

    /** 通过反射打开电话拨号界面 */
    private static void openPhoneScreen(ItemStack stack, InteractionHand hand) {
        try {
            Class<?> helper = Class.forName(
                    "org.agmas.noellesroles.client.utils.ClientItemHelper");
            helper.getMethod("openPhoneScreen", ItemStack.class, InteractionHand.class)
                    .invoke(null, stack, hand);
        } catch (Exception e) {
            // 服务端 —— 静默忽略
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.noellesroles.sixty_seconds_phone"));
    }
}
