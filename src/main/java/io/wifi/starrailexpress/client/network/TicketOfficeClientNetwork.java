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

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.TicketOfficeConfigScreen;
import io.wifi.starrailexpress.client.gui.screen.TicketOfficeShopScreen;
import io.wifi.starrailexpress.network.TicketPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class TicketOfficeClientNetwork {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TicketPayload.OpenOfficeConfig.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreen(new TicketOfficeConfigScreen(payload.pos(), payload.data())));
        });
        ClientPlayNetworking.registerGlobalReceiver(TicketPayload.OpenOfficeShop.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> client.setScreen(new TicketOfficeShopScreen(payload.pos(), payload.data())));
        });
    }
}
