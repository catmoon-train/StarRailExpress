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

import java.util.UUID;

/**
 * 建筑师拆墙包（服务端 -> 客户端）
 * 通知客户端移除一堵客户端墙
 */
public record BuilderRemoveWallS2CPacket(UUID wallId) implements CustomPacketPayload {
    
    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "builder_remove_wall");
    public static final Type<BuilderRemoveWallS2CPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderRemoveWallS2CPacket> CODEC;
    
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(wallId);
    }
    
    public static BuilderRemoveWallS2CPacket read(FriendlyByteBuf buf) {
        return new BuilderRemoveWallS2CPacket(buf.readUUID());
    }
    
    static {
        CODEC = StreamCodec.ofMember(BuilderRemoveWallS2CPacket::write, BuilderRemoveWallS2CPacket::read);
    }
}
