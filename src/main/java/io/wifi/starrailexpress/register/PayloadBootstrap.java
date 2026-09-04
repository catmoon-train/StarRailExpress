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

package io.wifi.starrailexpress.register;

import io.wifi.ConfigCompact.ConfigEvents;
import io.wifi.starrailexpress.network.DrawingBoardServerNetwork;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.agmas.noellesroles.game.roles.neutral.mafia.MafiaManager;
import org.agmas.noellesroles.init.ModPackets;
import org.agmas.noellesroles.register.RicePacketTypeRegister;
import pro.fazeclan.river.stupid_express.network.SplitBackCamera;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;
import pro.fazeclan.river.stupid_express.network.SplitPersonalitySwitchPacket;

/**
 * Single play-payload type registration entry. Packet classes stay next to
 * their features; callers must not register types ad-hoc.
 */
public final class PayloadBootstrap {
    private static boolean registered;

    private PayloadBootstrap() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        ConfigEvents.registerPayloadTypes();
        SREPayloadRegister.registerPayloadTypes();
        ModPackets.registerPackets();
        RicePacketTypeRegister.registerPayloadTypes();
        DrawingBoardServerNetwork.registerPayloadTypes();
        SplitPersonalityPackets.registerPayloadTypes();
        SplitPersonalitySwitchPacket.registerPayloadType();
        PayloadTypeRegistry.playS2C().register(SplitBackCamera.TYPE, SplitBackCamera.CODEC);
        MafiaManager.registerPayloadTypes();
    }
}
