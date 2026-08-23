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

import io.wifi.starrailexpress.client.SecurityCameraClientState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SecurityCameraModePayload(boolean enable, BlockPos cameraPos, float yaw, int cameraId) implements CustomPacketPayload {
    public static final Type<SecurityCameraModePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("starrailexpress", "security_camera_mode"));
    public static final StreamCodec<FriendlyByteBuf, SecurityCameraModePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SecurityCameraModePayload::enable,
            BlockPos.STREAM_CODEC, SecurityCameraModePayload::cameraPos,
            ByteBufCodecs.FLOAT, SecurityCameraModePayload::yaw,
            ByteBufCodecs.VAR_INT, SecurityCameraModePayload::cameraId,
            SecurityCameraModePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class ClientReceiver implements ClientPlayNetworking.PlayPayloadHandler<SecurityCameraModePayload>  {



        @Override
        public void receive(SecurityCameraModePayload payload, ClientPlayNetworking.Context context) {
            context.client().execute(() -> {
                if (payload.enable()) {
                    // 进入监控模式
                    SecurityCameraClientState.setCurrentCameraPos(payload.cameraPos());
                    SecurityCameraClientState.setSecurityMode(true);
                    // 设置初始视角角度
                    SecurityCameraClientState.setCurrentYaw(payload.yaw());
                    SecurityCameraClientState.lastCameraId = payload.cameraId();
                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
                } else {
                    // 退出监控模式
                    SecurityCameraClientState.setSecurityMode(false);
                    Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                    SecurityCameraClientState.setCurrentCameraPos(null);
                    // 重置视角角度
                    SecurityCameraClientState.setCurrentYaw(0.0f);
                    SecurityCameraClientState.lastCameraId = -1;
                }
            });
        }
    }
    
    public static class ServerReceiver {
        // 服务端接收来自客户端的包（如果需要的话）
    }
}