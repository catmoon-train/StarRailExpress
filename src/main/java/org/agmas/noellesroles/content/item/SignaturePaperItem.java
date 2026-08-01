/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

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
import org.agmas.noellesroles.init.ModItems;

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
                if (abpc.status == -1) {
                    abpc.status = 0;
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
