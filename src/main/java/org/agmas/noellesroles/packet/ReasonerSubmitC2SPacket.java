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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role_data.neutral.ReasonerRoleData;

public record ReasonerSubmitC2SPacket(int question, String answer) implements CustomPacketPayload {
    public static final Type<ReasonerSubmitC2SPacket> ID = new Type<>(Noellesroles.id("reasoner_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReasonerSubmitC2SPacket> CODEC = StreamCodec
            .ofMember(ReasonerSubmitC2SPacket::encode, ReasonerSubmitC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(question);
        buf.writeUtf(answer);
    }

    public static ReasonerSubmitC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ReasonerSubmitC2SPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(ReasonerSubmitC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            var data = io.wifi.starrailexpress.api.data.RoleData.getNullable(
                    org.agmas.noellesroles.role_data.neutral.ReasonerRoleData.class, context.player());
            if (data != null) {
                data.submitAnswer(context.player(), payload.question(), payload.answer());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
