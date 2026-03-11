package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.data.MapConfig;
import io.wifi.starrailexpress.data.ServerMapConfig;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public record SyncMapConfigPayload(List<MapConfig.MapEntry> maps) implements CustomPacketPayload {
    public static final Type<SyncMapConfigPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "sync_map_config"));
    public static final StreamCodec<FriendlyByteBuf, SyncMapConfigPayload> CODEC = StreamCodec.ofMember(SyncMapConfigPayload::encode, SyncMapConfigPayload::decode);

    public static SyncMapConfigPayload decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MapConfig.MapEntry> maps = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            String id = buf.readUtf();
            String displayName = buf.readUtf();
            String description = buf.readUtf();
            boolean canSelect = buf.readBoolean();
            String color = buf.readUtf();
            
            MapConfig.MapEntry entry = new MapConfig.MapEntry();
            entry.id = id;
            entry.displayName = displayName;
            entry.description = description;
            entry.canSelect = canSelect;
            entry.color = color;
            
            maps.add(entry);
        }
        
        return new SyncMapConfigPayload(maps);
    }

    public static void encode(SyncMapConfigPayload payload, FriendlyByteBuf buf) {
        buf.writeInt(payload.maps().size());
        
        for (MapConfig.MapEntry map : payload.maps()) {
            buf.writeUtf(map.getId());
            buf.writeUtf(map.getDisplayName());
            buf.writeUtf(map.getDescription());
            buf.writeBoolean(map.canSelect);
            buf.writeUtf(map.getColorStr());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void sendToPlayer(ServerPlayer player) {
        SyncMapConfigPayload payload = new SyncMapConfigPayload(ServerMapConfig.getInstance(player.getServer()).getMaps());
        ServerPlayNetworking.send(player, payload);
    }

    public static void sendToAllPlayers() {
        SyncMapConfigPayload payload = new SyncMapConfigPayload(ServerMapConfig.getInstance(SRE.SERVER).getMaps());
        PlayerLookup.all(SRE.SERVER).forEach(player -> ServerPlayNetworking.send(player, payload));
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            // 在客户端主线程上更新地图配置
            context.client().execute(() -> {
                // 更新客户端地图配置实例
                MapConfig.getInstance().maps = payload.maps();
            });
        });
    }
}