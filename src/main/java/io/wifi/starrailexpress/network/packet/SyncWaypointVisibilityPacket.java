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