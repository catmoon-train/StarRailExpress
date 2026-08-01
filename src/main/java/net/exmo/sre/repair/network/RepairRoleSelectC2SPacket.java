/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.repair.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairRoleDefinition;

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
