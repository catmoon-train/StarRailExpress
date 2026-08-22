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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.innocence.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.packet.OpenKeyForgeGuiS2CPacket;

public class KeyBlankItem extends Item {
    public KeyBlankItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!world.isClientSide && player instanceof ServerPlayer serverPlayer) {
            LocksmithInspirationComponent component = ModComponents.LOCKSMITH_INSPIRATION.get(serverPlayer);
            ServerPlayNetworking.send(serverPlayer, new OpenKeyForgeGuiS2CPacket(component.getInspirationPoints()));
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
