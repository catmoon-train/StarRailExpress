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
 * 阿蒙背包点选玩家包：客户端在背包界面点选一名成熟宿主，请求附身到其身上（进入附身）。
 * 附身后，阿蒙随时按 G 即完成夺舍（变成目标、令其死亡、本体处生成尸体；见 AmonPlayerComponent#finalizePossession）。
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
