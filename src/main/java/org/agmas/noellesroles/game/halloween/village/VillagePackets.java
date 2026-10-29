package org.agmas.noellesroles.game.halloween.village;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 商品仅由服务端下发；请求包只能指定一次性会话和商品序号。 */
public final class VillagePackets {
    private VillagePackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_" + name));
    }

    public record Offer(ResourceLocation item, int count, int pumpkins, int coins) {}

    public record Open(UUID npc, UUID nonce, List<Offer> offers) implements CustomPacketPayload {
        public static final Type<Open> ID = id("village_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.npc); b.writeUUID(p.nonce);
            b.writeCollection(p.offers, (buf, o) -> {
                buf.writeResourceLocation(o.item); buf.writeVarInt(o.count);
                buf.writeVarInt(o.pumpkins); buf.writeVarInt(o.coins);
            });
        }, b -> new Open(b.readUUID(), b.readUUID(), readList(b, 128, buf -> new Offer(
                buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()))));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Buy(UUID npc, UUID nonce, int index) implements CustomPacketPayload {
        public static final Type<Buy> ID = id("village_buy");
        public static final StreamCodec<RegistryFriendlyByteBuf, Buy> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.npc); b.writeUUID(p.nonce); b.writeVarInt(p.index);
        }, b -> new Buy(b.readUUID(), b.readUUID(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record Night(boolean active, int phase, int darkThreshold, List<UUID> souls) implements CustomPacketPayload {
        public static final Type<Night> ID = id("village_night");
        public static final StreamCodec<RegistryFriendlyByteBuf, Night> CODEC = StreamCodec.of((b, p) -> {
            b.writeBoolean(p.active); b.writeVarInt(p.phase); b.writeVarInt(p.darkThreshold);
            b.writeCollection(p.souls, (buf, uuid) -> buf.writeUUID(uuid));
        }, b -> new Night(b.readBoolean(), b.readVarInt(), b.readVarInt(), readList(b, 1024, buf -> buf.readUUID())));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }
    private static <T> List<T> readList(RegistryFriendlyByteBuf buf, int limit, java.util.function.Function<RegistryFriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > limit) throw new IllegalArgumentException("halloween list size");
        java.util.ArrayList<T> values = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(reader.apply(buf));
        return List.copyOf(values);
    }
}
