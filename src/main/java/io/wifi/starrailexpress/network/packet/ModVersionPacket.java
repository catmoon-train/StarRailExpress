package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record ModVersionPacket(String version) implements CustomPacketPayload {
    public static final Type<ModVersionPacket> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "mod_version_check"));
    public static final StreamCodec<FriendlyByteBuf, ModVersionPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(packet.version());
            },
            buf -> {
                return new ModVersionPacket(buf.readUtf());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<ModVersionPacket> {
        @Override
        public void receive(ModVersionPacket payload, ServerPlayNetworking.Context context) {
            ServerPlayer player = context.player();
            String clientVersion = payload.version();
            SRE.LOGGER.info("Server recieved response from client with mod version {}.", clientVersion);

            if (clientVersion != null) {
                if (!clientVersion.equals(SRE.modPacketVersion)) {
                    SRE.LOGGER.info(
                            "Server recieved response. But client's version {} doesn't match server's version {}",
                            clientVersion, SRE.modPacketVersion);
                    player.connection.disconnect(Component.translatableWithFallback("message.kick.outdated_client",
                            "Your client version is outdated! Server version %s, your version %s.",
                            SRE.modPacketVersion, clientVersion));
                }
            }
        }
    }
}