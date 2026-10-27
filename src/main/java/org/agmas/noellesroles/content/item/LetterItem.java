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

// import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
// import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.DropAndClearItem;

public class LetterItem extends SkinableItem implements DropAndClearItem {
    public static Runnable clientUiOpener = null;
    /** 客户端替换开局信。返回 true 表示已经打开，不再走地图信。 */
    public static java.util.function.BooleanSupplier clientReplacementOpener = null;

    public LetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemSkinType() {
        return io.wifi.starrailexpress.util.ItemSkinManager.SkinTypes.LETTER;
    }

    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        if (world.isClientSide()) {
            if (clientReplacementOpener != null && clientReplacementOpener.getAsBoolean()) {
                return InteractionResultHolder.success(user.getItemInHand(hand));
            }
            if (clientUiOpener != null) {
                clientUiOpener.run();
            }
        }
        return InteractionResultHolder.success(user.getItemInHand(hand));
    }
}
