package org.agmas.noellesroles.item;

import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SignaturePaperItem extends Item {

    public SignaturePaperItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (level.isClientSide)
            return InteractionResultHolder.success(itemStack);
        if (itemStack.is(ModItems.SIGNATURE_PAPER)) {
            if (player.isShiftKeyDown()) {
                // 生死状
                var abpc = SREAbilityPlayerComponent.KEY.get(player);
                if (abpc.charges == -1) {
                    abpc.setCharges(0);
                    itemStack = ModItems.LIFE_AND_DEATH_SHAPE.getDefaultInstance();
                    var itemName = Component.translatable("item.noellesroles.life_and_death_shape.rename",
                            player.getDisplayName());
                    itemStack.set(DataComponents.ITEM_NAME, itemName);
                    itemStack.set(SREDataComponentTypes.OWNER, player.getScoreboardName());
                    return InteractionResultHolder.success(itemStack);

                } else {

                    player.displayClientMessage(
                            Component.translatable("hud.noellesroles.star.you_have_did.life_and_death_shape")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    return InteractionResultHolder.fail(itemStack);
                }
            } else {
                itemStack = ModItems.SIGNED_PAPER.getDefaultInstance();
                var itemName = Component.translatable("item.noellesroles.signed_paper.rename",
                        player.getDisplayName());
                itemStack.set(DataComponents.ITEM_NAME, itemName);
                itemStack.set(SREDataComponentTypes.OWNER, player.getScoreboardName());
                return InteractionResultHolder.success(itemStack);
            }
        }
        return InteractionResultHolder.success(itemStack);
    }

}
