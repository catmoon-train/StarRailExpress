package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

public record WizardSwitchSpellC2SPacket() implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "wizard_switch_spell");
    public static final Type<WizardSwitchSpellC2SPacket> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WizardSwitchSpellC2SPacket> CODEC =
            StreamCodec.ofMember(WizardSwitchSpellC2SPacket::write, WizardSwitchSpellC2SPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
    }

    public static WizardSwitchSpellC2SPacket read(FriendlyByteBuf buf) {
        return new WizardSwitchSpellC2SPacket();
    }
}
