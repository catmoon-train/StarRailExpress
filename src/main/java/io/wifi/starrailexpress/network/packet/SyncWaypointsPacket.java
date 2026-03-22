package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.gui.screen.WaypointHUD;
import io.wifi.starrailexpress.data.WaypointData;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncWaypointsPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncWaypointsPacket> ID = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "sync_waypoints"));
    public static final StreamCodec<FriendlyByteBuf, SyncWaypointsPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.map(
                HashMap::new,
                ByteBufCodecs.STRING_UTF8,
                ByteBufCodecs.collection(
                    ArrayList::new,
                    WaypointData.STREAM_CODEC
                )
            ),
            SyncWaypointsPacket::getWaypoints,
            SyncWaypointsPacket::new
    );

    private final Map<String, List<WaypointData>> waypoints;

    public Map<String, List<WaypointData>> getWaypoints() {
        return waypoints;
    }

    public SyncWaypointsPacket(Map<String, List<WaypointData>> waypoints) {
        this.waypoints = waypoints;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(waypoints.size());
        
        for (Map.Entry<String, List<WaypointData>> entry : waypoints.entrySet()) {
            buf.writeUtf(entry.getKey()); // path
            List<WaypointData> pathWaypoints = entry.getValue();
            buf.writeInt(pathWaypoints.size());
            
            for (WaypointData wp : pathWaypoints) {
                buf.writeUtf(wp.getName());
                buf.writeInt(wp.getPos().getX());
                buf.writeInt(wp.getPos().getY());
                buf.writeInt(wp.getPos().getZ());
                buf.writeInt(wp.getColor());
            }
        }
    }

    public static SyncWaypointsPacket read(FriendlyByteBuf buf) {
        int pathCount = buf.readInt();
        Map<String, List<WaypointData>> waypoints = new HashMap<>();

        for (int i = 0; i < pathCount; i++) {
            String path = buf.readUtf();
            int waypointCount = buf.readInt();
            List<WaypointData> pathWaypoints = new ArrayList<>();

            for (int j = 0; j < waypointCount; j++) {
                String name = buf.readUtf();
                int x = buf.readInt();
                int y = buf.readInt();
                int z = buf.readInt();
                int color = buf.readInt();
                
                BlockPos pos = new BlockPos(x, y, z);
                WaypointData wp = new WaypointData(path, name, pos, color);
                pathWaypoints.add(wp);
            }
            
            waypoints.put(path, pathWaypoints);
        }

        return new SyncWaypointsPacket(waypoints);
    }

    public static void handle(SyncWaypointsPacket packet, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            // 清除现有的路径点
            WaypointHUD.clearAllWaypoints();
            
            // 添加新的路径点
            for (Map.Entry<String, List<WaypointData>> entry : packet.waypoints.entrySet()) {
                String path = entry.getKey();
                for (WaypointData wp : entry.getValue()) {
                    WaypointHUD.addWaypoint(wp.getPos(), wp.getName(), new Color(wp.getColor()));
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}