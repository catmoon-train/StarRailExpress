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

import io.wifi.starrailexpress.client.gui.screen.EffectGeneratorConfigScreen;
import io.wifi.starrailexpress.network.EffectGeneratorPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class EffectGeneratorClientNetwork {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(EffectGeneratorPayload.OpenConfig.TYPE, (payload, context) -> {
            context.client().execute(() -> context.client()
                    .setScreen(new EffectGeneratorConfigScreen(payload.pos(), payload.data())));
        });
    }
}
