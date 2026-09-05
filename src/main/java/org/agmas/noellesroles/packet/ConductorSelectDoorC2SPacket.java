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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/** 船长舱门调度：选择目标房间门；pos 为空表示取消。 */
public record ConductorSelectDoorC2SPacket(@Nullable BlockPos pos) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "conductor_select_door");
    public static final Type<ConductorSelectDoorC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConductorSelectDoorC2SPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(pos != null);
        if (pos != null) {
            buf.writeBlockPos(pos);
        }
    }

    public static ConductorSelectDoorC2SPacket read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return new ConductorSelectDoorC2SPacket(null);
        }
        return new ConductorSelectDoorC2SPacket(buf.readBlockPos());
    }

    static {
        CODEC = StreamCodec.ofMember(ConductorSelectDoorC2SPacket::write, ConductorSelectDoorC2SPacket::read);
    }
}
