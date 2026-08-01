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

/**
 * 服务端 -> 客户端：打开物资箱配置 GUI
 */
public record OpenSupplyCrateScreenS2CPacket(BlockPos blockPos) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "open_supply_crate_screen_s2c");
    public static final CustomPacketPayload.Type<OpenSupplyCrateScreenS2CPacket> ID =
            new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSupplyCrateScreenS2CPacket> CODEC =
            StreamCodec.ofMember(OpenSupplyCrateScreenS2CPacket::encode, OpenSupplyCrateScreenS2CPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.blockPos);
    }

    public static OpenSupplyCrateScreenS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenSupplyCrateScreenS2CPacket(buf.readBlockPos());
    }
}
