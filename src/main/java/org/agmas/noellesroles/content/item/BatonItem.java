package org.agmas.noellesroles.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BatonItem extends Item {
    public BatonItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        // 警棍主要逻辑在服务端攻击回调中实现，这里仅返回 consume 以触发挥动动作
        return InteractionResultHolder.pass(user.getItemInHand(hand));
    }
}
