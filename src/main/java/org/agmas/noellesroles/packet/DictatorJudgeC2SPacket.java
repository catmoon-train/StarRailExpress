package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.vigilante.DictatorRole;

import java.util.UUID;

/**
 * 独裁者裁决之剑提交：死因 + 凶手。
 * 服务端用「右键尸体时锁定的那具尸体」校验答案，正确则雷电处决凶手。
 */
public record DictatorJudgeC2SPacket(String deathReasonId, UUID killerUuid) implements CustomPacketPayload {
    public static final Type<DictatorJudgeC2SPacket> ID = new Type<>(Noellesroles.id("dictator_judge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DictatorJudgeC2SPacket> CODEC = StreamCodec
            .ofMember(DictatorJudgeC2SPacket::encode, DictatorJudgeC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(deathReasonId == null ? "" : deathReasonId);
        buf.writeUUID(killerUuid);
    }

    public static DictatorJudgeC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new DictatorJudgeC2SPacket(buf.readUtf(), buf.readUUID());
    }

    public static void handle(DictatorJudgeC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> DictatorRole.handleJudgment(context.player(),
                payload.deathReasonId(), payload.killerUuid()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
