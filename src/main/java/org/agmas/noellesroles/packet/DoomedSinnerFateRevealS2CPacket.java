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
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -&gt; 客户端：宿命的罪人「命运的启示」结果。
 * 携带目标名字与其最近若干次的杀人方式（死因 ResourceLocation 字符串）。
 */
public record DoomedSinnerFateRevealS2CPacket(
        String targetName,
        List<String> killMethods) implements CustomPacketPayload {

    public static final Type<DoomedSinnerFateRevealS2CPacket> ID =
            new Type<>(Noellesroles.id("doomed_sinner_fate_reveal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DoomedSinnerFateRevealS2CPacket> CODEC =
            StreamCodec.ofMember(DoomedSinnerFateRevealS2CPacket::encode, DoomedSinnerFateRevealS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(targetName);
        buf.writeVarInt(killMethods.size());
        killMethods.forEach(buf::writeUtf);
    }

    public static DoomedSinnerFateRevealS2CPacket decode(RegistryFriendlyByteBuf buf) {
        String targetName = buf.readUtf();
        int size = buf.readVarInt();
        List<String> killMethods = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            killMethods.add(buf.readUtf());
        }
        return new DoomedSinnerFateRevealS2CPacket(targetName, killMethods);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
