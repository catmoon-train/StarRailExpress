package org.agmas.noellesroles.packet;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;

public record RepairRoleSelectC2SPacket(String roleId) implements CustomPacketPayload {
    public static final Type<RepairRoleSelectC2SPacket> ID = new Type<>(Noellesroles.id("repair_role_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairRoleSelectC2SPacket> CODEC = StreamCodec
            .ofMember(RepairRoleSelectC2SPacket::encode, RepairRoleSelectC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(roleId);
    }

    public static RepairRoleSelectC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new RepairRoleSelectC2SPacket(buf.readUtf());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(RepairRoleSelectC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        var component = ModComponents.REPAIR_ROLES.get(player);
        RepairRoleDefinition.byId(payload.roleId()).ifPresent(role -> {
            if (component.owns(role)) {
                component.setSelectedRole(role);
                player.displayClientMessage(Component.translatable("message.noellesroles.repair.role_selected", role.displayName()), true);
            }
        });
    }
}
