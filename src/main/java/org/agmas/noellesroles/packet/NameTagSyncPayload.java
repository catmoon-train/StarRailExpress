package org.agmas.noellesroles.packet;

import io.wifi.StarRailExpressID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record NameTagSyncPayload(Map<UUID, String> nametags) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(StarRailExpressID.MOD_ID, "nametag_sync");
    public static final Type<NameTagSyncPayload> ID = new Type<>(PAYLOAD_ID);
    
    public static final StreamCodec<FriendlyByteBuf, NameTagSyncPayload> CODEC = StreamCodec.ofMember(
            NameTagSyncPayload::write,
            NameTagSyncPayload::read
    );
    
    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.nametags.size());
        
        for (Map.Entry<UUID, String> entry : this.nametags.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }
    
    private static NameTagSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<UUID, String> nametags = new HashMap<>();
        
        for (int i = 0; i < size; i++) {
            UUID uuid = buf.readUUID();
            String nametag = buf.readUtf();
            nametags.put(uuid, nametag);
        }
        
        return new NameTagSyncPayload(nametags);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
