package org.agmas.noellesroles.game.modes.echo.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 商店请求：购买卡包或售货机商品。
 *
 * <p>只带「买哪一个」和「在哪台机器」，价格与余额全部由服务端判定；
 * 客户端不能指定价格、品质或数量。
 */
public record EchoShopPayload(UUID matchId, Action action, String offerId, long machinePos)
        implements CustomPacketPayload {
    /** 购买动作。 */
    public enum Action { BUY_PACK, BUY_GOOD, OPEN }

    public static final Type<EchoShopPayload> ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/shop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoShopPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeUUID(value.matchId());
                buf.writeEnum(value.action());
                buf.writeUtf(value.offerId() == null ? "" : value.offerId(), 64);
                buf.writeLong(value.machinePos());
            },
            buf -> new EchoShopPayload(buf.readUUID(), buf.readEnum(Action.class), buf.readUtf(64), buf.readLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }

    public static EchoShopPayload pack(UUID matchId, EchoPackOffer offer) {
        return new EchoShopPayload(matchId, Action.BUY_PACK, offer.id(), Long.MIN_VALUE);
    }

    public static EchoShopPayload good(UUID matchId, long machinePos, EchoShopService.VendingGood good) {
        return new EchoShopPayload(matchId, Action.BUY_GOOD, good.id(), machinePos);
    }
}
