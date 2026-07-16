package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端→客户端：通知切换车辆摄像机（上车切第三人称，下车切回第一人称）。
 */
public record VehicleCameraS2CPacket(boolean thirdPerson) implements CustomPacketPayload {
    public static final Type<VehicleCameraS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "vehicle_camera"));

    public static final StreamCodec<FriendlyByteBuf, VehicleCameraS2CPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, VehicleCameraS2CPacket::thirdPerson,
                    VehicleCameraS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class ClientReceiver implements ClientPlayNetworking.PlayPayloadHandler<VehicleCameraS2CPacket> {
        @Override
        public void receive(VehicleCameraS2CPacket payload, ClientPlayNetworking.Context context) {
            context.client().execute(() -> {
                if (payload.thirdPerson()) {
                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
                } else {
                    Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                }
            });
        }
    }
}
