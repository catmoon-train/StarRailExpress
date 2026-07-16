package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcMenu;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcShop;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：在商人购买界面点了「买」。
 * {@link SixtySecondsNpcShop#buy} 会重校验模式/相位/类型/存活/距离/下标/库存/资金——
 * 隔空对 20 格外的商人发本包会被静默拒绝。
 */
public record NpcShopBuyC2SPacket(int entityId, int rowIndex, int count) implements CustomPacketPayload {
    public static final Type<NpcShopBuyC2SPacket> ID = new Type<>(Noellesroles.id("npc_shop_buy"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopBuyC2SPacket> CODEC =
            StreamCodec.ofMember(NpcShopBuyC2SPacket::encode, NpcShopBuyC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(rowIndex);
        buf.writeVarInt(count);
    }

    public static NpcShopBuyC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new NpcShopBuyC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(NpcShopBuyC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsNpcEntity npc = SixtySecondsNpcMenu.resolve(context.player(), payload.entityId());
        if (npc != null) {
            SixtySecondsNpcShop.buy(context.player(), npc, payload.rowIndex(), payload.count());
        }
    }
}
