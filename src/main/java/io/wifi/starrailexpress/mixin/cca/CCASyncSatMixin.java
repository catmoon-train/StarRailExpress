package io.wifi.starrailexpress.mixin.cca;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentProvider;
import org.ladysnake.cca.api.v3.component.sync.ComponentPacketWriter;
import org.ladysnake.cca.api.v3.component.sync.PlayerSyncPredicate;
import org.ladysnake.cca.internal.base.ComponentsInternals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(ComponentKey.class)
public abstract class CCASyncSatMixin<C extends Component>  {
@Inject(method = "syncWith(Lnet/minecraft/server/level/ServerPlayer;Lorg/ladysnake/cca/api/v3/component/ComponentProvider;Lorg/ladysnake/cca/api/v3/component/sync/ComponentPacketWriter;Lorg/ladysnake/cca/api/v3/component/sync/PlayerSyncPredicate;)V", at = @At("HEAD"), cancellable = true)
    public void syncWith(ServerPlayer player, ComponentProvider provider, ComponentPacketWriter writer, PlayerSyncPredicate predicate, CallbackInfo ci) {
    @SuppressWarnings("unchecked")
    ComponentKey<C> key = (ComponentKey<C>) (Object) this;
    if (predicate.shouldSyncWith(player)) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.serverLevel().registryAccess());
        writer.writeSyncPacket(buf, player);
        CustomPacketPayload payload = provider.toComponentPacket(key, predicate.isRequiredOnClient(), buf);
        if (payload != null) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                // 记录网络统计信息
                try {
                    long packetSize = buf.readableBytes();
                    String packetId = "CCA_" + payload.type().id().toString() + key.toString();
                    io.wifi.starrailexpress.network.NetworkStatistics.getInstance().recordPacketSend(
                        packetId, packetSize, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND, player.getName().getString()
                    );
                } catch (Exception e) {
                    // 忽略统计错误，避免影响正常功能
                }
                
                PacketSender var10000 = ServerPlayNetworking.getSender(player);
                Objects.requireNonNull(buf);
                var10000.sendPacket(payload, PacketSendListener.thenRun(buf::release));
            } else {
                if (predicate.isRequiredOnClient()) {
                    ServerGamePacketListenerImpl var7 = player.connection;
                    String var10001 = String.valueOf(payload.type().id());
                    var7.disconnect(net.minecraft.network.chat.Component.literal("This server requires Cardinal Components API (unhandled packet: " + var10001 + ")" + ComponentsInternals.getClientOptionalModAdvice()));
                }

                buf.release();
            }
        } else {
            buf.release();
        }
    }
    ci.cancel();
}


}
