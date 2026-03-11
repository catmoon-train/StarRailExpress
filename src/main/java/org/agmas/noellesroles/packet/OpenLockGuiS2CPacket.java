package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;

public record OpenLockGuiS2CPacket(BlockPos pos, UUID lockId, int lockLength) implements CustomPacketPayload {
    public static final ResourceLocation OPEN_LOCK_GUI_C2S = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "open_lock_gui_c2s");
    public static final CustomPacketPayload.Type<OpenLockGuiS2CPacket> ID = new CustomPacketPayload.Type<>(
            OPEN_LOCK_GUI_C2S);
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLockGuiS2CPacket> CODEC;

    static {
        CODEC = StreamCodec.ofMember(OpenLockGuiS2CPacket::encode, OpenLockGuiS2CPacket::decode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeUUID(this.lockId);
        buf.writeInt(this.lockLength);
    }

    public static OpenLockGuiS2CPacket decode(RegistryFriendlyByteBuf buf) {
        return new OpenLockGuiS2CPacket(buf.readBlockPos(), buf.readUUID(), buf.readInt());
    }
}
