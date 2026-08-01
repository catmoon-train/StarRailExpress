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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.content.block.SecurityMonitorBlock;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SecurityCameraExitRequestPayload() implements CustomPacketPayload {
    public static final Type<SecurityCameraExitRequestPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("starrailexpress", "security_camera_exit_request")
    );

    public static final StreamCodec<FriendlyByteBuf, SecurityCameraExitRequestPayload> CODEC =
            StreamCodec.unit(new SecurityCameraExitRequestPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class ServerReceiver implements ServerPlayNetworking.PlayPayloadHandler<SecurityCameraExitRequestPayload> {
        @Override
        public void receive(SecurityCameraExitRequestPayload payload, ServerPlayNetworking.Context context) {
            ServerPlayer player = context.player();
            // 退出监控模式
            SecurityMonitorBlock.exitSecurityMode(player);
        }
    }
}
