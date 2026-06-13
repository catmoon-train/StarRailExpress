package org.agmas.noellesroles.packet;

import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record UnifiedSkillInputC2SPacket(
        int slot,
        RoleSkill.Phase phase,
        @Nullable UUID target) implements CustomPacketPayload {
    public static final Type<UnifiedSkillInputC2SPacket> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "unified_skill_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnifiedSkillInputC2SPacket> CODEC =
            StreamCodec.ofMember(UnifiedSkillInputC2SPacket::write, UnifiedSkillInputC2SPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(slot);
        buf.writeEnum(phase);
        buf.writeBoolean(target != null);
        if (target != null) {
            buf.writeUUID(target);
        }
    }

    private static UnifiedSkillInputC2SPacket read(FriendlyByteBuf buf) {
        int slot = buf.readVarInt();
        RoleSkill.Phase phase = buf.readEnum(RoleSkill.Phase.class);
        UUID target = buf.readBoolean() ? buf.readUUID() : null;
        return new UnifiedSkillInputC2SPacket(slot, phase, target);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
