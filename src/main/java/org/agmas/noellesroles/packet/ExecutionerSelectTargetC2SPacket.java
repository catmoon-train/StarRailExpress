package org.agmas.noellesroles.packet;

import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Executioner选择目标的网络包
 * 用于客户端向服务器发送选中的目标玩家UUID
 */
public record ExecutionerSelectTargetC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation EXECUTIONER_SELECT_TARGET_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "executioner_select_target");
    public static final CustomPacketPayload.Type<ExecutionerSelectTargetC2SPacket> ID = new CustomPacketPayload.Type<>(EXECUTIONER_SELECT_TARGET_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecutionerSelectTargetC2SPacket> CODEC;

    public ExecutionerSelectTargetC2SPacket(UUID target) {
        this.target = target;
    }

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static ExecutionerSelectTargetC2SPacket read(FriendlyByteBuf buf) {
        return new ExecutionerSelectTargetC2SPacket(buf.readUUID());
    }

    public UUID target() {
        return this.target;
    }

    static {
        CODEC = StreamCodec.ofMember(ExecutionerSelectTargetC2SPacket::write, ExecutionerSelectTargetC2SPacket::read);
    }
}