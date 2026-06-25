package org.agmas.noellesroles.content.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;

/**
 * 巫师魔药：使用后进入冷却，获得大量魔素，并在接下来 60 秒内免疫一次致命攻击。
 */
public class WizardPotionItem extends Item {

    public WizardPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            WizardPlayerComponent.KEY.get(sp).usePotion(sp);
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
