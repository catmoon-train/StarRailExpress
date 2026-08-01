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
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

public record LotteryMachineResultS2CPacket(BlockPos blockPos, boolean success, String messageKey, ItemStack itemStack)
        implements CustomPacketPayload {
    public static final ResourceLocation LOTTERY_MACHINE_RESULT_S2C = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "lottery_machine_result_s2c");
    public static final Type<LotteryMachineResultS2CPacket> ID = new Type<>(LOTTERY_MACHINE_RESULT_S2C);
    public static final StreamCodec<RegistryFriendlyByteBuf, LotteryMachineResultS2CPacket> CODEC =
            StreamCodec.ofMember(LotteryMachineResultS2CPacket::encode, LotteryMachineResultS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
        buf.writeBoolean(this.success);
        buf.writeUtf(this.messageKey);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.itemStack);
    }

    public static LotteryMachineResultS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new LotteryMachineResultS2CPacket(
                buf.readBlockPos(),
                buf.readBoolean(),
                buf.readUtf(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }
}
