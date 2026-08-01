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

package io.wifi.ConfigCompact;

import io.wifi.ConfigCompact.network.RoleEnableInfoPacket;
import io.wifi.ConfigCompact.network.SyncConfigPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class ConfigEvents {
    public static void register() {
        PayloadTypeRegistry.playS2C().register(SyncConfigPayload.ID, SyncConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoleEnableInfoPacket.ID, RoleEnableInfoPacket.CODEC);
    }
}
