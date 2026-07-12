package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootStore;
import net.exmo.sre.sixtyseconds.loot.SixtySecondsLootTable;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/** 客户端→服务端：上传编辑后的 loot 表并落盘（OP/创造门控，解决 sync 不落盘的坑）。 */
public record LootTableSaveC2SPacket(SixtySecondsLootTable table) implements CustomPacketPayload {
    public static final Type<LootTableSaveC2SPacket> ID = new Type<>(Noellesroles.id("loot_table_save"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LootTableSaveC2SPacket> CODEC =
            StreamCodec.ofMember(LootTableSaveC2SPacket::encode, LootTableSaveC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        table.writeTo(buf);
    }

    public static LootTableSaveC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new LootTableSaveC2SPacket(SixtySecondsLootTable.readFrom(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(LootTableSaveC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.hasPermissions(2) && !player.isCreative()) {
            return;
        }
        if (player.level() instanceof ServerLevel level) {
            SixtySecondsLootStore.save(level, payload.table());
            player.displayClientMessage(
                    Component.literal("[60s] loot table saved").withStyle(ChatFormatting.GREEN), true);
        }
    }
}
