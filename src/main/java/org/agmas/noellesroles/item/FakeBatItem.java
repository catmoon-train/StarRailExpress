package org.agmas.noellesroles.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 假球棒
 * - 魔术师购买假疯狂模式时获得
 * - 无法击杀玩家，只能造成击退效果
 * - 模仿真实球棒的使用方式
 */
public class FakeBatItem extends Item {
    public FakeBatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }
}
