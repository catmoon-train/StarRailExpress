package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record GamblerSelectRoleC2SPacket(ResourceLocation roleId) implements CustomPacketPayload {
    public static final ResourceLocation GAMBLER_SELECT_ROLE_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "gambler_select_role");
    public static final Type<GamblerSelectRoleC2SPacket> ID = new Type<>(GAMBLER_SELECT_ROLE_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, GamblerSelectRoleC2SPacket> CODEC = StreamCodec.ofMember(
        (packet, buf) -> buf.writeResourceLocation(packet.roleId()),
        buf -> new GamblerSelectRoleC2SPacket(buf.readResourceLocation())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    // public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<GamblerSelectRoleC2SPacket> {
    //     @Override
    //     public void receive(@NotNull GamblerSelectRoleC2SPacket payload, ServerPlayNetworking.@NotNull Context context) {
    //         context.player().server.execute(() -> {
                
    //         });
    //     }
    // }
}