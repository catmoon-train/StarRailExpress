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
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.event.client.OnConfigSynced;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientConfigEvents {
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SyncConfigPayload.ID, (payload, context) -> {
            ConfigClassHandler.recieveConfigPackFromServer(payload.configId(), payload.content());
            OnConfigSynced.EVENT.invoker().onConfigSynced(payload.configId(),context.client());
        });

        ClientPlayNetworking.registerGlobalReceiver(RoleEnableInfoPacket.ID, (payload, context) -> {
            var packet = payload.packetInfo();
            boolean openUI = payload.openUI();
            RoleManageConfigUI.setRoleInfo(packet.roleInfo);
            RoleManageConfigUI.setModifierInfo(packet.modifierInfo);
            if (openUI) {
                context.client().execute(() -> {
                    context.client().setScreen(RoleManageConfigUI.getScreen(context.client().screen));
                });
            }
            SRE.LOGGER.info("Recieved role and modifer disabled infomation. [Size: {}, openUI: {}]",
                    packet.roleInfo.size() + packet.modifierInfo.size(), (openUI ? "Yes" : "No"));
        });
    }
}
