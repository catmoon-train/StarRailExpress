package io.wifi.starrailexpress.network.original;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.SRE;

public record SniperScopeStateS2CPayload(boolean scopeAttached) implements CustomPacketPayload {
    public static final Type<SniperScopeStateS2CPayload> TYPE = new Type<>(SRE.id("sniper_scope_state_s2c"));
    public static final StreamCodec<FriendlyByteBuf, SniperScopeStateS2CPayload> STREAM_CODEC = StreamCodec.ofMember(
            SniperScopeStateS2CPayload::write,
            SniperScopeStateS2CPayload::new
    );

    private SniperScopeStateS2CPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(scopeAttached);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
