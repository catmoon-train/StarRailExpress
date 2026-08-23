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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role_data.neutral.RecorderRoleData;
import org.agmas.noellesroles.role.ModRoles;

import java.util.UUID;

public record RecorderC2SPacket(UUID targetUuid, String roleId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecorderC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            Noellesroles.id("recorder_guess"));

    public static final StreamCodec<FriendlyByteBuf, RecorderC2SPacket> CODEC = CustomPacketPayload.codec(
            RecorderC2SPacket::write, RecorderC2SPacket::new);

    public RecorderC2SPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeUtf(roleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RecorderC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();

        context.server().execute(() -> {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

            if (!gameWorld.isRole(player, ModRoles.RECORDER))
                return;
            if (!GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player))
                return;
            RecorderRoleData recorder = io.wifi.starrailexpress.api.data.RoleData.getNullable(RecorderRoleData.class, player);
            ResourceLocation roleId = ResourceLocation.tryParse(payload.roleId());

            if (recorder != null && roleId != null) {
                recorder.addGuess(payload.targetUuid(), roleId);
            }
        });
    }
}