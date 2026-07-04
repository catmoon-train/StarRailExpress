package cn.zbx1425.minopp.network;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.ActionReport;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import cn.zbx1425.minopp.platform.ClientPlatform;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class C2SSeatControlPacket {

    public static final ResourceLocation ID = Mino.id("seat_control");

    public static void handleC2S(MinecraftServer server, ServerPlayer player, FriendlyByteBuf packet) {
        BlockPos gamePos = packet.readBlockPos();
        int action = packet.readInt();
        ServerLevel level = player.serverLevel();
        server.execute(() -> {
            if (level.getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
                List<CardPlayer> playersList = tableEntity.getPlayersList();
                CardPlayer cardPlayer = ItemHandCards.getCardPlayer(player);
//                if (!playersList.contains(cardPlayer)) {
//                    player.displayClientMessage(Component.translatable("game.minopp.play.no_player"), true);
//                    return;
//                }
                if (tableEntity.demo) {
                    player.displayClientMessage(Component.translatable("game.minopp.play.table_in_demo"), true);
                    return;
                }
                // Start or end the game
                switch (action) {
                    case 1 -> {
                        if (tableEntity.game == null) {
                            if (playersList.size() < 2) {
                                player.displayClientMessage(Component.translatable("game.minopp.play.no_enough_player"), true);
                                return;
                            }
                            tableEntity.startGame(cardPlayer);
                        }
                    }
                    case 0 -> {
                        if (tableEntity.game != null) tableEntity.destroyGame(cardPlayer);
                    }
                    case -1 -> {
                        if (tableEntity.game == null) tableEntity.resetSeats(cardPlayer);
                    }
                }
            }
        });
    }

    public static class Client {

        public static void sendGameEnableC2S(BlockPos gamePos, boolean gameEnable) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeBlockPos(gamePos);
            packet.writeInt(gameEnable ? 1 : 0);
            ClientPlatform.sendPacketToServer(ID, packet);
        }

        public static void sendResetSeatsC2S(BlockPos gamePos) {
            FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
            packet.writeBlockPos(gamePos);
            packet.writeInt(-1);
            ClientPlatform.sendPacketToServer(ID, packet);
        }
    }
}
