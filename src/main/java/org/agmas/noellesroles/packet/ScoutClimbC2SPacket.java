package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;

/**
 * 童子军攀爬请求包。
 *
 * <p>客户端检测到「空手右键紧贴的墙壁」后发 {@code start=true}
 * 并附上墙面法线（由墙指向玩家）；脱手时发 {@code start=false}。
 * 服务端只做薄转发：{@code ScoutRole.handleClimbPacket(...)}。
 */
public record ScoutClimbC2SPacket(boolean start, float normalX, float normalY, float normalZ)
        implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "scout_climb");
    public static final CustomPacketPayload.Type<ScoutClimbC2SPacket> ID =
            new CustomPacketPayload.Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ScoutClimbC2SPacket> CODEC =
            StreamCodec.ofMember(ScoutClimbC2SPacket::write, ScoutClimbC2SPacket::read);

    public static ScoutClimbC2SPacket start(Vec3 normal) {
        return new ScoutClimbC2SPacket(true, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    public static ScoutClimbC2SPacket stop() {
        return new ScoutClimbC2SPacket(false, 0f, 0f, 0f);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public Vec3 normal() {
        return new Vec3(normalX, normalY, normalZ);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(start);
        buf.writeFloat(normalX);
        buf.writeFloat(normalY);
        buf.writeFloat(normalZ);
    }

    public static ScoutClimbC2SPacket read(FriendlyByteBuf buf) {
        return new ScoutClimbC2SPacket(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
