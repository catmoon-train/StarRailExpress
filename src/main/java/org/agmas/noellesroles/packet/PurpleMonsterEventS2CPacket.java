package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Target-only state for the purple monster event. No entity is spawned on the server. */
public record PurpleMonsterEventS2CPacket(UUID eventId, Stage stage, double x, double y, double z,
                                          UUID skinPlayer, List<UUID> candidates)
        implements CustomPacketPayload {
    public enum Stage {
        DISGUISE,
        REVEAL,
        QUESTION,
        SELECT,
        ASSIMILATE_TRANSFORM,
        ASSIMILATE,
        ASSIMILATE_EFFECT,
        CLOSE
    }

    public static final Type<PurpleMonsterEventS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "purple_monster_event"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, PurpleMonsterEventS2CPacket> CODEC =
            net.minecraft.network.codec.StreamCodec.ofMember(PurpleMonsterEventS2CPacket::write,
                    PurpleMonsterEventS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeUUID(eventId);
        buf.writeVarInt(stage.ordinal());
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeBoolean(skinPlayer != null);
        if (skinPlayer != null) buf.writeUUID(skinPlayer);
        buf.writeVarInt(candidates.size());
        for (UUID candidate : candidates) buf.writeUUID(candidate);
    }

    private static PurpleMonsterEventS2CPacket read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        Stage stage = Stage.values()[buf.readVarInt()];
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        UUID skin = buf.readBoolean() ? buf.readUUID() : null;
        int size = Math.min(buf.readVarInt(), 128);
        List<UUID> candidates = new ArrayList<>(size);
        for (int i = 0; i < size; i++) candidates.add(buf.readUUID());
        return new PurpleMonsterEventS2CPacket(id, stage, x, y, z, skin, candidates);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
