package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.client.gui.screen.WaypointHUD;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SyncWaypointVisibilityPacket implements CustomPacketPayload {
    public static final Type<SyncWaypointVisibilityPacket> ID = new Type<>(ResourceLocation.tryBuild(SRE.MOD_ID, "sync_waypoint_visibility"));
    public static final StreamCodec<FriendlyByteBuf, SyncWaypointVisibilityPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeBoolean(packet.visible),
            buf -> new SyncWaypointVisibilityPacket(buf.readBoolean())
    );
    private final boolean visible;

    public SyncWaypointVisibilityPacket(boolean visible) {
        this.visible = visible;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(visible);
    }

    public static SyncWaypointVisibilityPacket read(FriendlyByteBuf buf) {
        boolean visible = buf.readBoolean();
        return new SyncWaypointVisibilityPacket(visible);
    }

    @Environment(EnvType.CLIENT)
    public static void handle(SyncWaypointVisibilityPacket packet, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            if (packet.visible) {
                WaypointHUD.showWaypoints();
            } else {
                WaypointHUD.hideWaypoints();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;

    }
}