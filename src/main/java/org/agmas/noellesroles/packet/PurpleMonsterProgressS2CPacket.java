package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** Server-authoritative assimilation progress for the Purple Monster HUD. */
public record PurpleMonsterProgressS2CPacket(int assimilated, int goal) implements CustomPacketPayload {
    public static final Type<PurpleMonsterProgressS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "purple_monster_progress"));

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf,
            PurpleMonsterProgressS2CPacket> CODEC = net.minecraft.network.codec.StreamCodec.ofMember(
                    PurpleMonsterProgressS2CPacket::write, PurpleMonsterProgressS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, assimilated));
        buf.writeVarInt(Math.max(1, goal));
    }

    private static PurpleMonsterProgressS2CPacket read(FriendlyByteBuf buf) {
        return new PurpleMonsterProgressS2CPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }
}
