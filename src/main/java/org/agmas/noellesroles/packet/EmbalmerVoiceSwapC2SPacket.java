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

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/** 嬉命人·语音互换：客户端在背包 Screen 中选中两名玩家后发出。 */
public record EmbalmerVoiceSwapC2SPacket(UUID player, UUID player2) implements CustomPacketPayload {
    public static final ResourceLocation VOICE_SWAP_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer_voice_swap");
    public static final Type<EmbalmerVoiceSwapC2SPacket> ID = new Type<>(VOICE_SWAP_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, EmbalmerVoiceSwapC2SPacket> CODEC =
            StreamCodec.ofMember(
                    (packet, buf) -> {
                        buf.writeUUID(packet.player());
                        buf.writeUUID(packet.player2());
                    },
                    buf -> new EmbalmerVoiceSwapC2SPacket(buf.readUUID(), buf.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
