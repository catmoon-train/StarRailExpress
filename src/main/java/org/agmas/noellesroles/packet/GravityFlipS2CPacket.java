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
 * 反重力方块状态同步（S2C，广播）。
 *
 * <p>反重力不经过药水效果（药水会被牛奶、清除类机制抹掉），客户端拿不到原版效果同步，
 * 因此由服务端在玩家重力方向改变时广播本包；客户端据此维护「谁正处于反重力」集合，
 * 供本地物理预测、模型倒立与相机翻转读取（{@code GravityFlipEffect#isFlipped}）。
 *
 * <p>客户端接收器按规范注册在 {@code NoellesrolesClient#onInitializeClient}，不写在 payload 里。
 */
public record GravityFlipS2CPacket(UUID playerId, boolean flipped) implements CustomPacketPayload {
    public static final ResourceLocation GRAVITY_FLIP_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "gravity_flip_sync");
    public static final Type<GravityFlipS2CPacket> ID = new Type<>(GRAVITY_FLIP_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GravityFlipS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUUID(packet.playerId());
                buf.writeBoolean(packet.flipped());
            },
            buf -> new GravityFlipS2CPacket(buf.readUUID(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
