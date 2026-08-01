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

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record VendingMachinesBuyC2SPacket(BlockPos blockPos, String item, int slot) implements CustomPacketPayload {
    public static final ResourceLocation VENDING_MACHINES_BUY_C2S = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "vending_machines_buy_c2s");
    public static final CustomPacketPayload.Type<VendingMachinesBuyC2SPacket> TYPE = new CustomPacketPayload.Type<>(
            VENDING_MACHINES_BUY_C2S);

    public static final StreamCodec<RegistryFriendlyByteBuf, VendingMachinesBuyC2SPacket> CODEC = StreamCodec.ofMember(
            VendingMachinesBuyC2SPacket::encode,
            VendingMachinesBuyC2SPacket::decode
    );

    public VendingMachinesBuyC2SPacket(BlockPos blockPos, String item) {
        this(blockPos, item, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeUtf(this.item);
        buf.writeInt(this.slot);
    }

    public static VendingMachinesBuyC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new VendingMachinesBuyC2SPacket(
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readInt()
        );
    }
}
