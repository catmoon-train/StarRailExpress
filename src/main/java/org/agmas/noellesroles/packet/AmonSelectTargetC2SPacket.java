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
 * 阿蒙背包点选玩家包：客户端在背包界面点选一名成熟宿主，请求寄宿到其体内。
 * 寄宿后，阿蒙跟随宿主但不操控其移动；停留足够时间后按 潜行+技能键 完成夺舍（见 AmonRoleData#finalizePossession）。
 */
public record AmonSelectTargetC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation AMON_SELECT_TARGET_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "amon_select_target");
    public static final CustomPacketPayload.Type<AmonSelectTargetC2SPacket> ID =
            new CustomPacketPayload.Type<>(AMON_SELECT_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, AmonSelectTargetC2SPacket> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static AmonSelectTargetC2SPacket read(FriendlyByteBuf buf) {
        return new AmonSelectTargetC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(AmonSelectTargetC2SPacket::write, AmonSelectTargetC2SPacket::read);
    }
}
