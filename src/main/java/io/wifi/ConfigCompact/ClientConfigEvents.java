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
            // 快照会被界面在渲染线程逐帧读取，所以写入也放到客户端线程：
            // 直接在网络线程 clear() + putAll() 会让界面读到「空快照」（开关短暂变成未设置）。
            context.client().execute(() -> {
                RoleManageConfigUI.setRoleInfo(packet.roleInfo);
                RoleManageConfigUI.setModifierInfo(packet.modifierInfo);
                // 人数快照：设置页要用它显示「自动 (N)」还是具体的强制值
                RoleManageConfigUI.setRoleCounts(packet.forcedKillerCount, packet.forcedVigilanteCount,
                        packet.forcedNeutralCount, packet.autoKillerCount, packet.autoVigilanteCount,
                        packet.autoNeutralCount);
                // 本局覆盖（时长 / 初始金币）
                RoleManageConfigUI.setRoundOverrides(packet.roundMinutes, packet.roundStartingMoney,
                        packet.defaultStartingMoney);
                if (openUI) {
                    context.client().setScreen(RoleManageConfigUI.getScreen(context.client().screen));
                }
            });
            SRE.LOGGER.info("Recieved role and modifer disabled infomation. [Size: {}, openUI: {}]",
                    packet.roleInfo.size() + packet.modifierInfo.size(), (openUI ? "Yes" : "No"));
        });
    }
}
