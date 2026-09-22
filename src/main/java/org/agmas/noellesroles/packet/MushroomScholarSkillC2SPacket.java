package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 菌菇学者技能切换/释放请求。 */
public record MushroomScholarSkillC2SPacket(boolean toggle) implements CustomPacketPayload {
    public static final Type<MushroomScholarSkillC2SPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mushroom_scholar_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MushroomScholarSkillC2SPacket> CODEC =
            StreamCodec.ofMember(MushroomScholarSkillC2SPacket::write, MushroomScholarSkillC2SPacket::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(toggle);
    }

    private static MushroomScholarSkillC2SPacket read(RegistryFriendlyByteBuf buf) {
        return new MushroomScholarSkillC2SPacket(buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
