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

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record KeyForgeGameC2Packet(int difficulty, boolean success) implements CustomPacketPayload {
    public static final ResourceLocation KEY_FORGE_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "key_forge_game");
    public static final Type<KeyForgeGameC2Packet> ID = new Type<>(KEY_FORGE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, KeyForgeGameC2Packet> CODEC;

    static {
        CODEC = StreamCodec.ofMember(KeyForgeGameC2Packet::write, KeyForgeGameC2Packet::read);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(this.difficulty);
        buf.writeBoolean(this.success);
    }

    public static KeyForgeGameC2Packet read(FriendlyByteBuf buf) {
        return new KeyForgeGameC2Packet(buf.readInt(), buf.readBoolean());
    }
}
