package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：解锁指定科技（服务端校验前置+废料）。 */
public record TechUnlockC2SPacket(String techId) implements CustomPacketPayload {
    public static final Type<TechUnlockC2SPacket> ID = new Type<>(Noellesroles.id("tech_unlock"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TechUnlockC2SPacket> CODEC =
            StreamCodec.ofMember(TechUnlockC2SPacket::encode, TechUnlockC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(techId);
    }

    public static TechUnlockC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new TechUnlockC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(TechUnlockC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsTechTree.handleUnlock(context.player(), payload.techId());
    }
}
