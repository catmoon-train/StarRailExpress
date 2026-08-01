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
 * 操纵师技能包
 * 用于客户端请求操控目标玩家
 */
public record ManipulatorC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation MANIPULATOR_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator");
    public static final CustomPacketPayload.Type<ManipulatorC2SPacket> ID = new CustomPacketPayload.Type<>(MANIPULATOR_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ManipulatorC2SPacket> CODEC;

    public ManipulatorC2SPacket(UUID player) {
        this.player = player;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static ManipulatorC2SPacket read(FriendlyByteBuf buf) {
        return new ManipulatorC2SPacket(buf.readUUID());
    }

    public UUID player() {
        return this.player;
    }

    static {
        CODEC = StreamCodec.ofMember(ManipulatorC2SPacket::write, ManipulatorC2SPacket::read);
    }
}