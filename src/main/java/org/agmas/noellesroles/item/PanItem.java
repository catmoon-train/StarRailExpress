// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class PanItem extends Item {
    public static Runnable openScreenCallback = null;
    public PanItem(Properties properties) {
        super(properties);
    }
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand){
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
        
        return InteractionResultHolder.success(stack);

    }
}
