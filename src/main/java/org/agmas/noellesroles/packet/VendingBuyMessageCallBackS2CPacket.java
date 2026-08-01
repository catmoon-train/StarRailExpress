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

public record VendingBuyMessageCallBackS2CPacket(String componentKey) implements CustomPacketPayload {
    public static final ResourceLocation VENDING_BUY_MESSAGE_CALLBACK_S2C = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "vending_buy_message_callback_s2c");
    public static final Type<VendingBuyMessageCallBackS2CPacket> ID = new Type<>(
            VENDING_BUY_MESSAGE_CALLBACK_S2C);

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingBuyMessageCallBackS2CPacket> CODEC = StreamCodec.ofMember(
            VendingBuyMessageCallBackS2CPacket::encode,
            VendingBuyMessageCallBackS2CPacket::decode
    );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.componentKey);
    }

    public static VendingBuyMessageCallBackS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new VendingBuyMessageCallBackS2CPacket(buf.readUtf());
    }
}
