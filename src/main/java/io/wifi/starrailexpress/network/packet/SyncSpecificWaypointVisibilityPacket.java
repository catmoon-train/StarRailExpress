/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.gui.screen.WaypointHUD;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class SyncSpecificWaypointVisibilityPacket implements CustomPacketPayload {
    public static final Type<SyncSpecificWaypointVisibilityPacket> ID = new Type<>(ResourceLocation.tryBuild(SRE.MOD_ID, "sync_specific_waypoint_visibility"));
    public static final StreamCodec<FriendlyByteBuf, SyncSpecificWaypointVisibilityPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBoolean(packet.visible);
                buf.writeUtf(packet.path);
                buf.writeUtf(packet.name);
            },
            buf -> new SyncSpecificWaypointVisibilityPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf())
    );
    private final boolean visible;
    private final String path;
    private final String name;

    public SyncSpecificWaypointVisibilityPacket(boolean visible, String path, String name) {
        this.visible = visible;
        this.path = path;
        this.name = name;
    }

    @Environment(EnvType.CLIENT)
    public static void handle(SyncSpecificWaypointVisibilityPacket packet, ClientPlayNetworking.Context context) {
        context.client().execute(() -> {
            if (packet.visible) {
                WaypointHUD.showSpecificWaypoint(packet.path, packet.name);
            } else {
                WaypointHUD.hideSpecificWaypoint(packet.path, packet.name);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public boolean isVisible() {
        return visible;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }
}