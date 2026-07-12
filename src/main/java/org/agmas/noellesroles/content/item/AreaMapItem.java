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
 * 区域地图。
 *
 * <p>自动生成当前游戏区域（AreasWorldComponent.playArea）的俯视地图，
 * 主要用于迷宫等地图。手持时在屏幕右侧显示 HUD 小地图；右键打开全屏
 * 地图界面（可缩放、拖动、切换 2D/3D、筛选任务点类型）。
 * 打开界面由客户端回调处理，物品本体不引用任何客户端类。
 */
public class AreaMapItem extends Item {

    /** 静态回调，由客户端设置用于打开地图界面。 */
    public static Runnable openScreenCallback = null;

    public AreaMapItem(Properties settings) {
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
        tooltip.add(Component.translatable("item.noellesroles.area_map.desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
