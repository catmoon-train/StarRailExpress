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

package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role_data.neutral.GamblerRoleData;
import org.jetbrains.annotations.NotNull;

public record GamblerSelectRoleC2SPacket(ResourceLocation roleId) implements CustomPacketPayload {
    public static final ResourceLocation GAMBLER_SELECT_ROLE_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "gambler_select_role");
    public static final Type<GamblerSelectRoleC2SPacket> ID = new Type<>(GAMBLER_SELECT_ROLE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblerSelectRoleC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeResourceLocation(packet.roleId()),
            buf -> new GamblerSelectRoleC2SPacket(buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<GamblerSelectRoleC2SPacket> {
        @Override
        public void receive(@NotNull GamblerSelectRoleC2SPacket payload,
                ServerPlayNetworking.@NotNull Context context) {
            final var player = context.player();
            // 复用网络包
            SREGameWorldComponent gameCCA = SREGameWorldComponent.KEY.get(player.level());
            if (gameCCA.isRunning() && gameCCA.getGameMode().equals(SREGameModes.CUSTOM_SELECTED_MODE) && gameCCA.isRole(player, SpecialGameModeRoles.CUSTOM_PENDING)) {
                CustomRoleGameModeWorldComponent.KEY.get(player.level()).playerSelectedRole(player, payload.roleId());
                return;
            }
            GamblerRoleData component = io.wifi.starrailexpress.api.data.RoleData.getNullable(GamblerRoleData.class, player);
            if (component != null) {
                component.selectRole(payload.roleId());
            }
        }
    }
}