package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：60s 枪械开火请求（携带客户端准星命中实体 id，-1=未命中）。
 * 服务端在 {@link SixtySecondsGunItem#handleShoot} 里统一做 冷却/弹药/命中结算，客户端只负责准星射线与表现。
 */
public record SixtySecondsGunShootC2SPacket(int target) implements CustomPacketPayload {
    public static final Type<SixtySecondsGunShootC2SPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_gun_shoot"));
    public static final StreamCodec<FriendlyByteBuf, SixtySecondsGunShootC2SPacket> CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SixtySecondsGunShootC2SPacket::target,
                    SixtySecondsGunShootC2SPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(SixtySecondsGunShootC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        SixtySecondsGunItem.handleShoot(player, payload.target());
    }
}
