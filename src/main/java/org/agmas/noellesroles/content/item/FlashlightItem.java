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

import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FlashlightItem extends Item {

    public FlashlightItem(Properties properties) {
        super(properties.component(SREDataComponentTypes.STATUS, false));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        final var item = user.getItemInHand(hand);
        if (user.isSpectator())
            return InteractionResultHolder.pass(item);
        if (!item.has(SREDataComponentTypes.STATUS)) {
            item.set(SREDataComponentTypes.STATUS, false);
        }
        boolean nowStatus = item.get(SREDataComponentTypes.STATUS);
        item.set(SREDataComponentTypes.STATUS, !nowStatus);
        world.playSound(user, user.blockPosition(), TMMSounds.BLOCK_LIGHT_TOGGLE, SoundSource.PLAYERS);
        return InteractionResultHolder.success(item);
    }
}
