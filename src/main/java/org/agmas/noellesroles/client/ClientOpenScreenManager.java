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

import org.agmas.noellesroles.packet.OpenScreenPayload;
import org.agmas.noellesroles.utils.OpenScreenManager;

import io.wifi.rhythm.client.RhythmMapManager;
import io.wifi.rhythm.client.screen.RhythmGameListScreen;
import io.wifi.rhythm.client.screen.RhythmGameScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
        if (id.equals(OpenScreenManager.RHYTHM_GAME_SCREEN_ROLE)) {
            var map = RhythmMapManager.randomMap();
            if (map.isPresent()) {
                screen = new RhythmGameScreen(map.get(), true);
            } else {
                context.client().player
                        .displayClientMessage(Component.translatable("skill.noellesroles.mistia.error.no_music").withStyle(ChatFormatting.RED), true);
            }
        }
        if (screen != null) {
            final var finalScreen = screen;
            context.client().execute(() -> {
                context.client().setScreen(finalScreen);
            });
        }
    }

}
