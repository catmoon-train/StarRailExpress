package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.*;

public record EmbalmerSkinSwapS2CPacket(Map<UUID, UUID> swaps, Map<UUID, Float> pitches, int durationTicks)
        implements CustomPacketPayload {
    public static final Type<EmbalmerSkinSwapS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer_skin_swap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmbalmerSkinSwapS2CPacket> CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeInt(p.swaps.size());
                for (var e : p.swaps.entrySet()) { buf.writeUUID(e.getKey()); buf.writeUUID(e.getValue()); }
                buf.writeInt(p.pitches.size());
                for (var e : p.pitches.entrySet()) { buf.writeUUID(e.getKey()); buf.writeFloat(e.getValue()); }
                buf.writeInt(p.durationTicks);
            }, buf -> {
                int sz = buf.readInt(); Map<UUID, UUID> s = new LinkedHashMap<>();
                for (int i = 0; i < sz; i++) s.put(buf.readUUID(), buf.readUUID());
                int pz = buf.readInt(); Map<UUID, Float> p = new HashMap<>();
                for (int i = 0; i < pz; i++) p.put(buf.readUUID(), buf.readFloat());
                return new EmbalmerSkinSwapS2CPacket(s, p, buf.readInt());
            });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    public static EmbalmerSkinSwapS2CPacket clear() {
        return new EmbalmerSkinSwapS2CPacket(Map.of(), Map.of(), 0);
    }
}
