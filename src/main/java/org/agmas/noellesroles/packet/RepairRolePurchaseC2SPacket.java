package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

public record RepairRolePurchaseC2SPacket(String roleId) implements CustomPacketPayload {
    public static final Type<RepairRolePurchaseC2SPacket> ID = new Type<>(Noellesroles.id("repair_role_purchase"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairRolePurchaseC2SPacket> CODEC = StreamCodec
            .ofMember(RepairRolePurchaseC2SPacket::encode, RepairRolePurchaseC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(roleId);
    }

    public static RepairRolePurchaseC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairRolePurchaseC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairRolePurchaseC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        player.displayClientMessage(Component.translatable("message.noellesroles.repair.buy_roles_outside")
                .withStyle(ChatFormatting.YELLOW), true);
    }
}
