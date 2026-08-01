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

package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

@Environment(EnvType.CLIENT)
public interface ClientHeldItemSwitchEvent {
    Event<ClientHeldItemSwitchEvent> EVENT = createArrayBacked(ClientHeldItemSwitchEvent.class,
            listeners -> (player, mainHand, offHand) -> {
                for (ClientHeldItemSwitchEvent listener : listeners) {
                    listener.onSwitch(player, mainHand, offHand);
                }
            });

    void onSwitch(LocalPlayer player, ItemStack mainHand, ItemStack offHand);
}
