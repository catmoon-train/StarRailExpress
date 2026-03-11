package io.wifi.starrailexpress.mixin.network;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.status.ClientStatusPacketListener;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;

@Mixin(ClientboundStatusResponsePacket.class)
public abstract class ServerListFixMixin implements Packet<ClientStatusPacketListener> {
//    @Redirect(method = "write",at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;encodeAsJson(Lcom/mojang/serialization/Codec;Ljava/lang/Object;)V"))
//    public <T>  void write(PacketByteBuf instance, Codec<T> codec, T value) {
//        var value1 = (ServerMetadata) value;
//        instance.encodeAsJson(ServerMetadata.CODEC,new ServerMetadata(value1.description(), Optional.of(new ServerMetadata.Players(-1,1, List.of(new GameProfile(UUID.randomUUID(),"服务器维护中，建地图中，请进二服")))),value1.version(),value1.favicon(),value1.secureChatEnforced()));
//    }
}
