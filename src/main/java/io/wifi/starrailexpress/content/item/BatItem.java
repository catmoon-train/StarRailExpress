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

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeBat;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.TrainWeapon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BatItem extends SkinableItem implements HeldLikeBat, TrainWeapon {
    public static final ResourceLocation ITEM_ID = SRE.id("bat");

    public BatItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user.isCreative()) {
            SREPlayerPsychoComponent playerPsychoComponent = SREPlayerPsychoComponent.KEY.get(user);
            if (playerPsychoComponent.getPsychoTicks() > 0) {
                playerPsychoComponent.stopPsycho();
            } else {
                playerPsychoComponent.startPsycho();
            }
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            return InteractionResultHolder.success(user.getItemInHand(hand));
        }

        return super.use(world, user, hand);
    }

    @Override
    public String getItemSkinType() {
        return "bat";
    }
}
