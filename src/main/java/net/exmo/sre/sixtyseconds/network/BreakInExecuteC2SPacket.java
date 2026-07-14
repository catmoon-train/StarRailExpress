package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 【已废弃】客户端→服务端：撬棍/开锁器远程选队闯入。闯入已改走统一门菜单
 * （{@code SixtySecondsDoorMenu.ACTION_BREAK_*} → {@code SixtySecondsBreakIn.executeAtDoor}，
 * 必须对着目标队的探索区避难所门操作），本包处理置空——保留注册仅为兼容旧客户端不断连。
 */
public record BreakInExecuteC2SPacket(int targetTeamId) implements CustomPacketPayload {
    public static final Type<BreakInExecuteC2SPacket> ID = new Type<>(Noellesroles.id("break_in_execute"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakInExecuteC2SPacket> CODEC =
            StreamCodec.ofMember(BreakInExecuteC2SPacket::encode, BreakInExecuteC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(targetTeamId);
    }

    public static BreakInExecuteC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new BreakInExecuteC2SPacket(buf.readVarInt());
    }

    public static void handle(BreakInExecuteC2SPacket payload, ServerPlayNetworking.Context context) {
        // no-op：远程选队闯入通道已关闭（防绕过「必须走到门口」的伪造包），见类注释
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
