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
 * 星级地图——带有探索迷雾和星级区域标注的全功能星图。
 *
 * <p>手持时在屏幕右侧显示 HUD 小地图（带迷雾效果）；右键打开全屏星图界面，
 * 支持拖拽、缩放、2D/3D 视图切换。已探索区域展示真实地形颜色，未探索区域
 * 覆盖深色迷雾。星级区域以彩色边框和星级标签标注在地图上。
 * </p>
 */
public class StarMapItem extends Item {

    /** 静态回调，由客户端设置用于打开全屏星图界面。 */
    public static Runnable openScreenCallback = null;

    public StarMapItem(Properties settings) {
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
        tooltip.add(Component.translatable("item.noellesroles.star_map.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.star_map.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
