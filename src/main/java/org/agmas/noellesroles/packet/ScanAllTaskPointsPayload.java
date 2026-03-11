package org.agmas.noellesroles.packet;

import java.util.HashMap;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ScanAllTaskPointsPayload(HashMap<BlockPos, Integer> taskBlocks) implements CustomPacketPayload {
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "client_scan_task");
    public static final Type<ScanAllTaskPointsPayload> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ScanAllTaskPointsPayload> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(new TaskBlocksInfos(this.taskBlocks()).getStringBuf());
    }

    public static ScanAllTaskPointsPayload read(FriendlyByteBuf buf) {
        String data = buf.readUtf();
        return new ScanAllTaskPointsPayload(new TaskBlocksInfos(data).getTaskBlockInfosMap());
    }

    static {
        CODEC = StreamCodec.ofMember(ScanAllTaskPointsPayload::write, ScanAllTaskPointsPayload::read);
    }
}