package org.agmas.noellesroles.game.halloween;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 只发给实际进入者；anchor 为 null 表示退出并清除边界，不暴露其它玩家的据点位置。 */
public record PumpkinHideoutS2CPayload(ResourceLocation dimension, BlockPos anchor) implements CustomPacketPayload {
    public static final Type<PumpkinHideoutS2CPayload> ID = new Type<>(Noellesroles.id("pumpkin_hideout"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PumpkinHideoutS2CPayload> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeResourceLocation(packet.dimension());
                buf.writeBoolean(packet.anchor() != null);
                if (packet.anchor() != null) buf.writeBlockPos(packet.anchor());
            }, buf -> new PumpkinHideoutS2CPayload(buf.readResourceLocation(),
                    buf.readBoolean() ? buf.readBlockPos() : null));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
