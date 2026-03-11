package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 操纵师技能包
 * 用于客户端请求操控目标玩家
 */
public record ManipulatorC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation MANIPULATOR_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator");
    public static final CustomPacketPayload.Type<ManipulatorC2SPacket> ID = new CustomPacketPayload.Type<>(MANIPULATOR_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ManipulatorC2SPacket> CODEC;

    public ManipulatorC2SPacket(UUID player) {
        this.player = player;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static ManipulatorC2SPacket read(FriendlyByteBuf buf) {
        return new ManipulatorC2SPacket(buf.readUUID());
    }

    public UUID player() {
        return this.player;
    }

    static {
        CODEC = StreamCodec.ofMember(ManipulatorC2SPacket::write, ManipulatorC2SPacket::read);
    }
}