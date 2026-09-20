package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

public record PurpleMonsterEventC2SPacket(UUID eventId, Action action, UUID selectedPlayer)
        implements CustomPacketPayload {
    public enum Action { OBSERVED, ANSWER, SELECT }

    public static final Type<PurpleMonsterEventC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "purple_monster_event_action"));
    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, PurpleMonsterEventC2SPacket> CODEC =
            net.minecraft.network.codec.StreamCodec.ofMember(PurpleMonsterEventC2SPacket::write,
                    PurpleMonsterEventC2SPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(eventId);
        buf.writeVarInt(action.ordinal());
        buf.writeBoolean(selectedPlayer != null);
        if (selectedPlayer != null) buf.writeUUID(selectedPlayer);
    }

    private static PurpleMonsterEventC2SPacket read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Action action = Action.values()[buf.readVarInt()];
        UUID selected = buf.readBoolean() ? buf.readUUID() : null;
        return new PurpleMonsterEventC2SPacket(id, action, selected);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
