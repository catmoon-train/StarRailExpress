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

/**
 * 电报员发送消息网络包
 * 
 * 从客户端发送到服务端，包含：
 * - 要发送的匿名消息内容
 */
public record TelegrapherC2SPacket(String message) implements CustomPacketPayload {
    
    public static final ResourceLocation TELEGRAPHER_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "telegrapher_message");
    public static final Type<TelegrapherC2SPacket> ID = new Type<>(TELEGRAPHER_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, TelegrapherC2SPacket> CODEC;
    
    public TelegrapherC2SPacket(String message) {
        this.message = message;
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.message);
    }
    
    public static TelegrapherC2SPacket read(FriendlyByteBuf buf) {
        return new TelegrapherC2SPacket(buf.readUtf());
    }
    
    public String message() {
        return this.message;
    }
    
    static {
        CODEC = StreamCodec.ofMember(TelegrapherC2SPacket::write, TelegrapherC2SPacket::read);
    }
}