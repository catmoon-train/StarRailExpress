package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.gamemode.CustomRoleGameModeWorldComponent;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent;
import org.jetbrains.annotations.NotNull;

public record GamblerSelectRoleC2SPacket(ResourceLocation roleId) implements CustomPacketPayload {
    public static final ResourceLocation GAMBLER_SELECT_ROLE_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "gambler_select_role");
    public static final Type<GamblerSelectRoleC2SPacket> ID = new Type<>(GAMBLER_SELECT_ROLE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblerSelectRoleC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeResourceLocation(packet.roleId()),
            buf -> new GamblerSelectRoleC2SPacket(buf.readResourceLocation()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<GamblerSelectRoleC2SPacket> {
        @Override
        public void receive(@NotNull GamblerSelectRoleC2SPacket payload,
                ServerPlayNetworking.@NotNull Context context) {
            final var player = context.player();
            // 复用网络包
            SREGameWorldComponent gameCCA = SREGameWorldComponent.KEY.get(player.level());
            if (gameCCA.isRunning() && gameCCA.getGameMode().equals(SREGameModes.CUSTOM_SELECTED_MODE) && gameCCA.isRole(player, SpecialGameModeRoles.CUSTOM_PENDING)) {
                CustomRoleGameModeWorldComponent.KEY.get(player.level()).playerSelectedRole(player, payload.roleId());
                return;
            }
            GamblerPlayerComponent component = GamblerPlayerComponent.KEY.get(player);
            component.selectRole(payload.roleId());
        }
    }
}