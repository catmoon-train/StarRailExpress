package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.data.MapConfig;
import io.wifi.starrailexpress.data.ServerMapConfig;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.Objects;

public final class ShowSelectedMapUIPayload implements CustomPacketPayload {
    public static final Type<ShowSelectedMapUIPayload> ID = new Type<>(SRE.id("show_selected_map_ui"));
    public static final StreamCodec<FriendlyByteBuf, ShowSelectedMapUIPayload> CODEC = CustomPacketPayload
            .codec(ShowSelectedMapUIPayload::write, ShowSelectedMapUIPayload::new);
    private  String serverConfig;

    public ShowSelectedMapUIPayload(String serverConfig) {
        this.serverConfig = serverConfig;
    }

    public ShowSelectedMapUIPayload(FriendlyByteBuf friendlyByteBuf) {
        this(friendlyByteBuf.readUtf());
    }

    public ShowSelectedMapUIPayload(boolean joinLater) {
        if (joinLater) {
            this.serverConfig = MapConfig.gson.toJson(ServerMapConfig.cache_maps) ;

        }else {
        }
    }

    public ShowSelectedMapUIPayload(ServerMapConfig mp) {
        this(convertServerMapConfigToString(mp));
    }

    public static String convertServerMapConfigToString(ServerMapConfig mp) {
        MapConfig cfg = new MapConfig();
        cfg.maps = mp.getRandomMaps();
        return MapConfig.gson.toJson(cfg);
    }

    public ShowSelectedMapUIPayload(MapConfig mp) {
        this(MapConfig.gson.toJson(mp));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(serverConfig);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public String serverConfig() {
        return serverConfig;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (ShowSelectedMapUIPayload) obj;
        return Objects.equals(this.serverConfig, that.serverConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverConfig);
    }

    @Override
    public String toString() {
        return "ShowSelectedMapUIPayload[" +
                "serverConfig=" + serverConfig + ']';
    }

}
