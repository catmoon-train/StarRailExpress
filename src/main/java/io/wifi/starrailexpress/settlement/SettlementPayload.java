package io.wifi.starrailexpress.settlement;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** 小控制包：快照按不超过 24000 字符分片发送，避免大局回放超过单包上限。 */
public record SettlementPayload(UUID session, int action, int index, int total, String data)
        implements CustomPacketPayload {
    public static final int PREPARE = 0, SNAPSHOT = 1, TELEPORTED = 2, PLAY = 3, STOP = 4;
    public static final Type<SettlementPayload> ID = new Type<>(SRE.id("settlement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPayload> CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.session); b.writeVarInt(p.action); b.writeVarInt(p.index);
                b.writeVarInt(p.total); b.writeUtf(p.data, 24000); },
            b -> new SettlementPayload(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(24000)));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
