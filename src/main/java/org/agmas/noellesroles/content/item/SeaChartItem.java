package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
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
 * 海图物品——末日60秒海岛远征的实体海图。
 * <p>
 * 右键使用时请求服务端下发最新海图数据并打开全屏海图界面（可拖动、缩放）。
 * 在海图上可点击已探明岛屿扬帆前往，或站在登岛点附近脱战后返回住所（10秒划船动画）。
 * </p>
 */
public class SeaChartItem extends Item {

    /** 静态回调，由客户端设置用于请求海图数据并打开界面。 */
    public static Runnable openScreenCallback = null;

    public SeaChartItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (world.isClientSide() && openScreenCallback != null) {
            openScreenCallback.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.sea_chart.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
