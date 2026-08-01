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

package io.wifi.starrailexpress.content.item;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BlackoutItem extends Item {

    public BlackoutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        var item = user.getItemInHand(hand);
        if (user.getCooldowns().isOnCooldown(this)) {
            InteractionResultHolder.pass(item);
        }
        if (!user.isSpectator() && !world.isClientSide())
            if (!SREPlayerShopComponent.useBlackout(user))
                return InteractionResultHolder.fail(item);
        if (user.isCreative()) {
            return InteractionResultHolder.consume(item);
        }
        return InteractionResultHolder.consume(item.consumeAndReturn(1, user));
        // return super.use(world, user, hand);
    }
}
