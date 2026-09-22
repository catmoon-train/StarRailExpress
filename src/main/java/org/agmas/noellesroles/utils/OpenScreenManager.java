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

package org.agmas.noellesroles.utils;

import java.util.HashMap;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.OpenScreenPayload;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface OpenScreenManager {
    HashMap<ResourceLocation, Component> SCREENS = new HashMap<>();

    public static void init() {
    }

    public static ResourceLocation register(ResourceLocation id, Component name) {
        SCREENS.put(id, name);
        return id;
    }

    public static void openScreen(ServerPlayer player, ResourceLocation screenId) {
        if (screenId == null)
            return;
        ServerPlayNetworking.send(player, new OpenScreenPayload(screenId));
    }

    ResourceLocation RHYTHM_GAME_SCREEN = register(SRE.id("rhythm_game"), Component.translatable("gui.rhythm.title"));
    ResourceLocation RHYTHM_GAME_SCREEN_ROLE = register(SRE.id("rhythm_game_role"), Component.translatable("gui.rhythm.title"));
    /** 程序员的终端界面（客户端映射见 ClientOpenScreenManager） */
    ResourceLocation PROGRAMMER_TERMINAL_SCREEN = register(Noellesroles.id("programmer_terminal"),
            Component.translatable("screen.noellesroles.terminal.title"));
    ResourceLocation PRIEST_CHANT_SCREEN = register(Noellesroles.id("priest_chant"),
            Component.translatable("screen.noellesroles.priest.title"));
    ResourceLocation MUSHROOM_CULTIVATION_SCREEN = register(Noellesroles.id("mushroom_cultivation"),
            Component.translatable("screen.noellesroles.mushroom.title"));
}
