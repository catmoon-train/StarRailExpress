package io.wifi.starrailexpress.network.packet;

import com.google.common.reflect.TypeToken;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.data.MapConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record SyncRoomToPlayerPayload(Map<UUID, Integer> data) implements CustomPacketPayload {
    public static final Type<SyncRoomToPlayerPayload> ID = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "sync_roomtoplayer"));
    public static final StreamCodec<FriendlyByteBuf, SyncRoomToPlayerPayload> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeUtf(MapConfig.gson.toJson(packet.data()));
            },
            buf -> {
                String dat = buf.readUtf();
                java.lang.reflect.Type type = new TypeToken<Map<UUID, Integer>>() {
                }.getType();

                var data1 = new HashMap<UUID, Integer>();
                try {
                    Map<UUID, Integer> data2 = MapConfig.gson.fromJson(dat, type);
                    data1.putAll(data2);
                } catch (Exception e) {
                    data1.clear();
                }
                return new SyncRoomToPlayerPayload(data1);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}