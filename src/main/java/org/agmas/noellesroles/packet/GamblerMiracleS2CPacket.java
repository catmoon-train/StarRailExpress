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
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * S2C 网络包：服务端广播给所有客户端，触发赌徒 1% 奇迹的客户端视觉特效。
 * 服务端只发一个包，客户端自行渲染粒子和音效，减少服务端网络压力。
 */
public record GamblerMiracleS2CPacket(Vec3 victimPos) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gambler_miracle");
    public static final Type<GamblerMiracleS2CPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblerMiracleS2CPacket> CODEC =
            StreamCodec.ofMember(GamblerMiracleS2CPacket::write, GamblerMiracleS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVec3(victimPos);
    }

    public static GamblerMiracleS2CPacket read(FriendlyByteBuf buf) {
        return new GamblerMiracleS2CPacket(buf.readVec3());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
