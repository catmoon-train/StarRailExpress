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
                if (isClientVersionOld(SRE.modPacketVersion, clientVersion)) {
                    SRE.LOGGER.info(
                            "Server recieved response. But client's version {} doesn't match server's version {}",
                            clientVersion, SRE.modPacketVersion);
                    player.connection.disconnect(Component.translatableWithFallback("message.kick.outdated_client",
                            "Your client version is outdated! Server version %s, your version %s.",
                            SRE.modPacketVersion, clientVersion));
                }
            }
        }

        private static boolean isClientVersionOld(String serverVersion, String clientVersion) {
            if (serverVersion == null || clientVersion == null) {
                return false;
            }
            if (serverVersion.equals(clientVersion)) {
                return false;
            }

            String[] serverParts = serverVersion.split("\\.");
            String[] clientParts = clientVersion.split("\\.");
            int minLen = Math.min(serverParts.length, clientParts.length);

            for (int i = 0; i < minLen; i++) {
                try {
                    int s = Integer.parseInt(serverParts[i]);
                    int c = Integer.parseInt(clientParts[i]);
                    if (s != c) {
                        // 客户端数字更小 → 客户端更旧
                        return c < s;
                    }
                } catch (NumberFormatException e) {
                    // 当前段不是纯数字，回退到整个版本字符串的字典序比较
                    // 若客户端字典序小于服务器，则认为客户端更旧
                    return clientVersion.compareTo(serverVersion) < 0;
                }
            }

            // 所有公共段相同且全部是数字，比较段数（多段者为新）
            if (serverParts.length == clientParts.length) {
                return false; // 完全相等
            } else {
                return clientParts.length < serverParts.length; // 客户端段数少 → 旧
            }
        }
    }
}