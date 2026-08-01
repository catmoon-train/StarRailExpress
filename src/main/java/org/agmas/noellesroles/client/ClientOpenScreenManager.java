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

package org.agmas.noellesroles.client;

import org.agmas.noellesroles.client.utils.OpenScreenManager;
import org.agmas.noellesroles.packet.OpenScreenPayload;

import io.wifi.rhythm.client.screen.RhythmGameListScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * ClientOpenScreenManager
 */
public class ClientOpenScreenManager {

    public static void openScreen(OpenScreenPayload payload, Context context) {
        Screen screen = null;
        ResourceLocation id = payload.id();
        if (id.equals(OpenScreenManager.RHYTHM_GAME_SCREEN)) {
            screen = new RhythmGameListScreen(null);
        }
        if (screen != null) {
            final var finalScreen = screen;
            context.client().execute(() -> {
                context.client().setScreen(finalScreen);
            });
        }
    }

}
