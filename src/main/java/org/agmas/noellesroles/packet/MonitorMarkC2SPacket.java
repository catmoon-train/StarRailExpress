package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 监察员标记目标数据包 (客户端 -> 服务端)
 */
public record MonitorMarkC2SPacket(UUID target) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MonitorMarkC2SPacket> ID = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "monitor_mark"));

    public static final StreamCodec<FriendlyByteBuf, MonitorMarkC2SPacket> CODEC = CustomPacketPayload.codec(
            MonitorMarkC2SPacket::write,
            MonitorMarkC2SPacket::new);

    public MonitorMarkC2SPacket(FriendlyByteBuf buf) {
        this(buf.readUUID());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}