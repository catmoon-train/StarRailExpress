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

public record OpenKeyForgeGuiS2CPacket(int inspirationPoints) implements CustomPacketPayload {
    public static final ResourceLocation OPEN_KEY_FORGE_GUI_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "open_key_forge_gui");
    public static final Type<OpenKeyForgeGuiS2CPacket> ID = new Type<>(OPEN_KEY_FORGE_GUI_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenKeyForgeGuiS2CPacket> CODEC;

    static {
        CODEC = StreamCodec.ofMember(OpenKeyForgeGuiS2CPacket::encode, OpenKeyForgeGuiS2CPacket::decode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(this.inspirationPoints);
    }

    public static OpenKeyForgeGuiS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenKeyForgeGuiS2CPacket(buf.readInt());
    }
}
