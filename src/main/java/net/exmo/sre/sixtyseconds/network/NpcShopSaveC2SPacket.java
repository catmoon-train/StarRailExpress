package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopStore;
import net.exmo.sre.sixtyseconds.shop.SixtySecondsShopTable;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：上传编辑后的商店表并落盘（OP/创造门控，解决 sync 不落盘的坑）。 */
public record NpcShopSaveC2SPacket(SixtySecondsShopTable table) implements CustomPacketPayload {
    public static final Type<NpcShopSaveC2SPacket> ID = new Type<>(Noellesroles.id("npc_shop_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcShopSaveC2SPacket> CODEC =
            StreamCodec.ofMember(NpcShopSaveC2SPacket::encode, NpcShopSaveC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        table.writeTo(buf);
    }

    public static NpcShopSaveC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new NpcShopSaveC2SPacket(SixtySecondsShopTable.readFrom(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(NpcShopSaveC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.hasPermissions(2) && !player.isCreative()) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            SixtySecondsShopStore.save(level, payload.table());
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.npc.shop_saved").withStyle(ChatFormatting.GREEN), true);
        }
    }
}
