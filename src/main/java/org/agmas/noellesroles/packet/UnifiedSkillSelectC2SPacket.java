package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record UnifiedSkillSelectC2SPacket(int slot) implements CustomPacketPayload {
    public static final Type<UnifiedSkillSelectC2SPacket> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "unified_skill_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnifiedSkillSelectC2SPacket> CODEC =
            StreamCodec.ofMember(UnifiedSkillSelectC2SPacket::write, UnifiedSkillSelectC2SPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
    }

    private static UnifiedSkillSelectC2SPacket read(FriendlyByteBuf buf) {
        return new UnifiedSkillSelectC2SPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
