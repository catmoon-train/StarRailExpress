package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.vigilante.DictatorRole;

import java.util.UUID;

/**
 * 独裁之书提交：目标玩家 + 猜测的职业。
 * 猜中则目标位置劈下闪电并按「裁断」死因判死；无论对错独裁之书都会消耗。
 */
public record DictatorGuessC2SPacket(UUID targetUuid, String roleId) implements CustomPacketPayload {
    public static final Type<DictatorGuessC2SPacket> ID = new Type<>(Noellesroles.id("dictator_guess"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DictatorGuessC2SPacket> CODEC = StreamCodec
            .ofMember(DictatorGuessC2SPacket::encode, DictatorGuessC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(targetUuid);
        buf.writeUtf(roleId == null ? "" : roleId);
    }

    public static DictatorGuessC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new DictatorGuessC2SPacket(buf.readUUID(), buf.readUtf());
    }

    public static void handle(DictatorGuessC2SPacket payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> DictatorRole.handleGuess(context.player(),
                payload.targetUuid(), payload.roleId()));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
