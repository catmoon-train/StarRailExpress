package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

public final class CakeIngredientsItem extends Item {
    public CakeIngredientsItem(Properties properties) { super(properties); }
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!SREGameWorldComponent.KEY.get(level).isRole(player, ModRoles.CAKE_MAKER)) return InteractionResultHolder.fail(stack);
        int empty = 0;
        for (int i = 0; i < 9; i++) if (player.getInventory().getItem(i).isEmpty()) empty++;
        if (empty < 4) {
            player.displayClientMessage(Component.translatable("message.noellesroles.cake_maker.hotbar_full").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getInventory().add(new ItemStack(Items.WHEAT, 3));
        player.getInventory().add(new ItemStack(Items.SUGAR, 2));
        player.getInventory().add(ModItems.CAKE_EGG.getDefaultInstance());
        player.getInventory().add(new ItemStack(ModItems.CAKE_MILK_BUCKET, 3));
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
