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

import java.util.UUID;

/**
 * 传递盒通用会话状态同步包（服务端 -> 客户端）。
 *
 * <p>非果果快递 / 文文新闻玩家没有 {@link org.agmas.noellesroles.role_data.innocence.AyayayaRoleData}
 * 职业数据，会话只存在于服务端。界面需要知道会话是否仍然有效、双方是否已确认，
 * 因此由服务端显式把状态镜像给发起者和接收者。</p>
 */
public record PostmanDeliveryStateS2CPacket(
        UUID senderUuid,
        UUID targetUuid,
        String senderName,
        String targetName,
        boolean senderConfirmed,
        boolean targetConfirmed,
        boolean active
) implements CustomPacketPayload {

    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "postman_delivery_state");
    public static final Type<PostmanDeliveryStateS2CPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PostmanDeliveryStateS2CPacket> CODEC;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(senderUuid);
        buf.writeUUID(targetUuid);
        buf.writeUtf(senderName == null ? "" : senderName);
        buf.writeUtf(targetName == null ? "" : targetName);
        buf.writeBoolean(senderConfirmed);
        buf.writeBoolean(targetConfirmed);
        buf.writeBoolean(active);
    }

    public static PostmanDeliveryStateS2CPacket read(RegistryFriendlyByteBuf buf) {
        return new PostmanDeliveryStateS2CPacket(
                buf.readUUID(), buf.readUUID(), buf.readUtf(), buf.readUtf(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    static {
        CODEC = StreamCodec.ofMember(PostmanDeliveryStateS2CPacket::write, PostmanDeliveryStateS2CPacket::read);
    }
}
