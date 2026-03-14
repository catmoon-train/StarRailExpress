package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 阴谋之书页物品
 *
 * 功能：
 * - 阴谋家专属物品
 * - 右键打开玩家/角色选择 GUI
 * - 使用后消耗
 */
public class ConspiracyPageItem extends Item {
    
    // 静态回调，由客户端设置用于打开GUI
    public static Runnable openScreenCallback = null;
    
    public ConspiracyPageItem(Properties settings) {
        super(settings);
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        
        // 验证：玩家必须存活
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.fail(stack);
        }
        
        // 客户端：打开GUI
        if (world.isClientSide()) {
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
        }
        
        // 返回 success 但不消耗物品，等猜测完成后再消耗
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}