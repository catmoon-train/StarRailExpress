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
 * Executioner选择目标的网络包
 * 用于客户端向服务器发送选中的目标玩家UUID
 */
public record ExecutionerSelectTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation EXECUTIONER_SELECT_TARGET_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "executioner_select_target");
    public static final CustomPacketPayload.Type<ExecutionerSelectTargetC2SPacket> ID = new CustomPacketPayload.Type<>(EXECUTIONER_SELECT_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecutionerSelectTargetC2SPacket> CODEC;

    public ExecutionerSelectTargetC2SPacket(UUID target) {
        this.target = target;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static ExecutionerSelectTargetC2SPacket read(FriendlyByteBuf buf) {
        return new ExecutionerSelectTargetC2SPacket(buf.readUUID());
    }

    public UUID target() {
        return this.target;
    }

    static {
        CODEC = StreamCodec.ofMember(ExecutionerSelectTargetC2SPacket::write, ExecutionerSelectTargetC2SPacket::read);
    }
}