package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record SkincrawlerSkinS2CPacket(UUID skincrawlerId, UUID stolenSkinId) implements CustomPacketPayload {
    public static final Type<SkincrawlerSkinS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "skincrawler_skin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkincrawlerSkinS2CPacket> CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeUUID(p.skincrawlerId);
                buf.writeBoolean(p.stolenSkinId != null);
                if (p.stolenSkinId != null) buf.writeUUID(p.stolenSkinId);
            }, buf -> {
                UUID sid = buf.readUUID();
                UUID skin = buf.readBoolean() ? buf.readUUID() : null;
                return new SkincrawlerSkinS2CPacket(sid, skin);
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
