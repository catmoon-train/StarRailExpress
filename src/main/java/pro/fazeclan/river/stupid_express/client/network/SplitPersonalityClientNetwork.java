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

package pro.fazeclan.river.stupid_express.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import pro.fazeclan.river.stupid_express.network.SplitPersonalityPackets;
import pro.fazeclan.river.stupid_express.network.SplitPersonalitySwitchPacket;

public class SplitPersonalityClientNetwork {
    
    public static void sendChoicePacket(int choice) {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SplitPersonalityPackets.SplitPersonalityChoicePayload(choice));
        }
    }
    
    public static void sendSwitchPacket() {
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SplitPersonalitySwitchPacket());
        }
    }
    
    public static void sendSacrificeChoice() {
        sendChoicePacket(0); // 0 = SACRIFICE
    }
    
    public static void sendBetrayChoice() {
        sendChoicePacket(1); // 1 = BETRAY
    }
}
