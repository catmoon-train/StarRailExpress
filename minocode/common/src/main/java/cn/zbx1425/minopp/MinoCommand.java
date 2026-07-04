package cn.zbx1425.minopp;

import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.block.BlockMinoTable;
import cn.zbx1425.minopp.game.ActionReport;
import cn.zbx1425.minopp.game.CardGame;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.item.ItemHandCards;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

public class MinoCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("minopp")
                .then(Commands.literal("shout")
                        .executes(context -> {
                            boolean success = Mino.onServerChatMessage("mino", context.getSource().getPlayerOrException());
                            if (!success) throw new SimpleCommandExceptionType(Component.translatable("game.minopp.play.no_game")).create();
                            return 1;
                        }))
                .then(Commands.literal("set_table_award").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ItemStack holdingItem = player.getMainHandItem();
                        if (holdingItem.isEmpty()) {
                            context.getSource().sendFailure(Component.literal("Requirement: Hold an item"));
                            return 0;
                        }
                        if (!player.getBlockStateOn().is(Mino.BLOCK_MINO_TABLE.get())) {
                            context.getSource().sendFailure(Component.literal("Requirement: Stand on a table"));
                            return 0;
                        }
                        BlockPos corePos = BlockMinoTable.getCore(player.getBlockStateOn(), player.getOnPos());
                        if (player.serverLevel().getBlockEntity(corePos) instanceof BlockEntityMinoTable tableEntity) {
                            tableEntity.award = holdingItem.copy();
                            tableEntity.sync();
                            context.getSource().sendSuccess(() -> Component.literal("Table award set"), true);
                            return 1;
                        } else {
                            context.getSource().sendFailure(Component.literal("Requirement: Stand on a table"));
                            return 0;
                        }
                    }))
                .then(Commands.literal("set_table_demo").requires((commandSourceStack) -> commandSourceStack.hasPermission(2))
                        .then(Commands.argument("demo", BoolArgumentType.bool())
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!player.getBlockStateOn().is(Mino.BLOCK_MINO_TABLE.get())) {
                                context.getSource().sendFailure(Component.literal("Requirement: Stand on a table"));
                                return 0;
                            }
                            BlockPos corePos = BlockMinoTable.getCore(player.getBlockStateOn(), player.getOnPos());
                            if (player.serverLevel().getBlockEntity(corePos) instanceof BlockEntityMinoTable tableEntity) {
                                tableEntity.demo = BoolArgumentType.getBool(context, "demo");
                                tableEntity.sync();
                                context.getSource().sendSuccess(() -> Component.literal("Table demo set"), true);
                                return 1;
                            } else {
                                context.getSource().sendFailure(Component.literal("Requirement: Stand on a table"));
                                return 0;
                            }
                        })))
        );
    }

    private static final SimpleCommandExceptionType ERROR_NO_GAME = new SimpleCommandExceptionType(Component.translatable("game.minopp.play.no_game"));


    private static void withPlayerAndGame(CommandContext<CommandSourceStack> context, BiConsumer<CardGame, CardPlayer> action) throws CommandSyntaxException {
        BlockPos gamePos = ItemHandCards.getHandCardGamePos(context.getSource().getPlayerOrException());
        if (gamePos == null) throw ERROR_NO_GAME.create();
        if (context.getSource().getLevel().getBlockEntity(gamePos) instanceof BlockEntityMinoTable tableEntity) {
            if (tableEntity.game == null) throw ERROR_NO_GAME.create();
            CardPlayer cardPlayer = tableEntity.game.deAmputate(ItemHandCards.getCardPlayer(context.getSource().getPlayerOrException()));
            if (cardPlayer == null) throw ERROR_NO_GAME.create();
            action.accept(tableEntity.game, cardPlayer);
            tableEntity.sync();
        } else {
            throw ERROR_NO_GAME.create();
        }
    }
}
