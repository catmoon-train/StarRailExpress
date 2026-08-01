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

public record LotteryMachineDrawC2SPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final ResourceLocation LOTTERY_MACHINE_DRAW_C2S = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "lottery_machine_draw_c2s");
    public static final Type<LotteryMachineDrawC2SPacket> TYPE = new Type<>(LOTTERY_MACHINE_DRAW_C2S);
    public static final StreamCodec<RegistryFriendlyByteBuf, LotteryMachineDrawC2SPacket> CODEC =
            StreamCodec.ofMember(LotteryMachineDrawC2SPacket::encode, LotteryMachineDrawC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
    }

    public static LotteryMachineDrawC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new LotteryMachineDrawC2SPacket(buf.readBlockPos());
    }
}
