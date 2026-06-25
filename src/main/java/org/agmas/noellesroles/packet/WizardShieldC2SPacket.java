package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 巫师“盔甲护身”：客户端在背包点击玩家头像后，请求为目标赋予一次护盾。
 */
public record WizardShieldC2SPacket(UUID player) implements CustomPacketPayload {
    public static final ResourceLocation WIZARD_SHIELD_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "wizard_shield");
    public static final CustomPacketPayload.Type<WizardShieldC2SPacket> ID =
            new CustomPacketPayload.Type<>(WIZARD_SHIELD_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WizardShieldC2SPacket> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.player);
    }

    public static WizardShieldC2SPacket read(FriendlyByteBuf buf) {
        return new WizardShieldC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(WizardShieldC2SPacket::write, WizardShieldC2SPacket::read);
    }
}
