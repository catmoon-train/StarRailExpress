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

package io.wifi.starrailexpress.mixin.network;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientboundStatusResponsePacket.class)
public abstract class ServerListFixMixin implements Packet<ClientStatusPacketListener> {
//    @Redirect(method = "write",at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;encodeAsJson(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"))
//    public <T>  void write(PacketByteBuf instance, Codec<T> codec, T value) {
//        var value1 = (ServerMetadata) value;
//        instance.encodeAsJson(ServerMetadata.CODEC,new ServerMetadata(value1.description(), Optional.of(new ServerMetadata.Players(-1,1, List.of(new GameProfile(UUID.randomUUID(),"服务器维护中，建地图中，请进二服")))),value1.version(),value1.favicon(),value1.secureChatEnforced()));
//    }
}
