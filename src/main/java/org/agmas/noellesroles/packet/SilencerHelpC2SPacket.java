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

public record SilencerHelpC2SPacket(UUID targetPlayer) implements CustomPacketPayload {
    public static final ResourceLocation SILENCER_HELP_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "silencer_help");
    public static final CustomPacketPayload.Type<SilencerHelpC2SPacket> ID = new CustomPacketPayload.Type<>(SILENCER_HELP_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SilencerHelpC2SPacket> CODEC;

    public SilencerHelpC2SPacket(UUID targetPlayer) {
        this.targetPlayer = targetPlayer;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(this.targetPlayer);
    }

    public static SilencerHelpC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new SilencerHelpC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(SilencerHelpC2SPacket::write, SilencerHelpC2SPacket::read);
    }
}
