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