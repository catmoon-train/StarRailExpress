package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record ToggleInsaneSkillC2SPacket(boolean toggle) implements CustomPacketPayload {
    public static final ResourceLocation TOGGLE_INSANE_SKILL_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "toggle_insane_skill");
    public static final Type<ToggleInsaneSkillC2SPacket> ID = new Type<>(TOGGLE_INSANE_SKILL_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleInsaneSkillC2SPacket> CODEC;


    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(toggle);
    }

    public static ToggleInsaneSkillC2SPacket read(FriendlyByteBuf buf) {
        return new ToggleInsaneSkillC2SPacket(buf.readBoolean());
    }


    static {
        CODEC = StreamCodec.ofMember(ToggleInsaneSkillC2SPacket::write, ToggleInsaneSkillC2SPacket::read);
    }
}