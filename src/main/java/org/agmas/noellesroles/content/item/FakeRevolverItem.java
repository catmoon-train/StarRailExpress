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

import io.wifi.starrailexpress.content.item.RevolverItem;
import io.wifi.starrailexpress.network.original.GunShootPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class FakeRevolverItem extends RevolverItem {
    public FakeRevolverItem(Properties settings) {
        super(settings);
    }

    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND)
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        if (user.getItemInHand(hand).getDamageValue() < user.getItemInHand(hand).getMaxDamage()) {
            user.getItemInHand(hand).hurtAndBreak(1, user, EquipmentSlot.MAINHAND);
            if (world.isClientSide) {
                ClientPlayNetworking.send(new GunShootPayload(-1));
                user.setXRot(user.getXRot() - 4.0F);
                spawnHandParticle();
            }
        }
        return InteractionResultHolder.consume(user.getItemInHand(hand));
    }
}
