package io.wifi.starrailexpress.settlement;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

/** 请求必须同时匹配会话和阶段，不能跨局完成旧的传送或跳过。 */
public record SettlementRequest(UUID session, int action) implements CustomPacketPayload {
    public static final int BLACK = 0, READY = 1, HOLD = 2, RELEASE = 3, FINISHED = 4;
    public static final Type<SettlementRequest> ID = new Type<>(SRE.id("settlement_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementRequest> CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.session); b.writeVarInt(p.action); },
            b -> new SettlementRequest(b.readUUID(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
