package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.fourthroom.card.CardRegistry;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomGameManager;
import org.agmas.noellesroles.game.modes.fourthroom.game.FourthRoomPlayerState;
import org.agmas.noellesroles.game.modes.fourthroom.scene.FourthRoomSceneGenerator;
import org.agmas.noellesroles.game.modes.fourthroom.shop.FourthRoomShopItem;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FourthRoomCommand {
    private static final SuggestionProvider<CommandSourceStack> CARD_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(CardRegistry.ids(), builder);
    private static final SuggestionProvider<CommandSourceStack> SHOP_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(FourthRoomShopItem.values()).map(FourthRoomShopItem::id), builder);

    private FourthRoomCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:fourthroom")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("generate_test_scene")
                .requires(source -> source.hasPermission(2))
                .executes(context -> generateTestScene(context.getSource(), defaultSceneOrigin(context.getSource())))
                .then(Commands.argument("origin", BlockPosArgument.blockPos())
                    .executes(context -> generateTestScene(context.getSource(),
                        BlockPosArgument.getLoadedBlockPos(context, "origin")))))
                .then(Commands.literal("reveal").executes(context -> reveal(context.getSource())))
                .then(Commands.literal("play")
                        .then(Commands.argument("cardId", StringArgumentType.word()).suggests(CARD_SUGGESTIONS)
                                .executes(context -> play(context.getSource(),
                                        StringArgumentType.getString(context, "cardId"), null))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> play(context.getSource(),
                                                StringArgumentType.getString(context, "cardId"),
                                                EntityArgument.getPlayer(context, "target"))))))
                .then(Commands.literal("endturn").executes(context -> endTurn(context.getSource())))
                .then(Commands.literal("buy")
                        .then(Commands.argument("itemId", StringArgumentType.word()).suggests(SHOP_SUGGESTIONS)
                                .executes(context -> buy(context.getSource(),
                                        StringArgumentType.getString(context, "itemId")))))
                .then(Commands.literal("use_item")
                        .then(Commands.argument("itemId", StringArgumentType.word()).suggests(SHOP_SUGGESTIONS)
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> useItem(context.getSource(),
                                                StringArgumentType.getString(context, "itemId"),
                                                EntityArgument.getPlayer(context, "target"))))))
                .then(Commands.literal("task_complete").executes(context -> completeTask(context.getSource())))
                .then(Commands.literal("search_notes").executes(context -> searchNotes(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        FourthRoomGameManager manager = FourthRoomGameManager.of(source.getLevel());
        var data = manager.data();
        source.sendSuccess(() -> Component.translatable("command.fourth_room.status.phase", data.phase.name(), data.activeTaskId, data.nextRotationTick, data.rooms.size(), data.sceneLayout.generated), false);
        if (data.sceneLayout.generated) {
            source.sendSuccess(() -> Component.translatable("command.fourth_room.status.scene", data.sceneLayout.lobbyPos, data.sceneLayout.duelArenaPos), false);
        }
        try {
            ServerPlayer player = source.getPlayerOrException();
            FourthRoomPlayerState state = data.players.get(player.getUUID());
            if (state != null) {
                List<String> hand = state.hand.stream().map(card -> card.cardId() + (card.gold() ? "*" : "")).toList();
                source.sendSuccess(() -> Component.translatable("command.fourth_room.status.player", state.roomId, state.coins, String.join(", ", hand)), false);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private static int generateTestScene(CommandSourceStack source, BlockPos origin) {
        var layout = new FourthRoomSceneGenerator(source.getLevel()).generate(origin);
        FourthRoomGameManager.of(source.getLevel()).syncMatchState();
        source.sendSuccess(() -> Component.translatable("command.fourth_room.generate_scene", origin, layout.rooms.size()), true);
        return 1;
    }

    private static BlockPos defaultSceneOrigin(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return player.blockPosition().below();
        } catch (Exception ignored) {
            return source.getLevel().getSharedSpawnPos();
        }
    }

    private static int reveal(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).revealOwnIdentity(player.getUUID());
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.reveal.success" : "command.fourth_room.reveal.failure"), false);
        return success ? 1 : 0;
    }

    private static int play(CommandSourceStack source, String cardId, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).playCard(player.getUUID(), cardId, target == null ? null : target.getUUID());
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.play.success" : "command.fourth_room.play.failure"), false);
        return success ? 1 : 0;
    }

    private static int endTurn(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).endTurn(player.getUUID());
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.end_turn.success" : "command.fourth_room.end_turn.failure"), false);
        return success ? 1 : 0;
    }

    private static int buy(CommandSourceStack source, String itemId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FourthRoomShopItem item = FourthRoomShopItem.byId(itemId.toLowerCase(Locale.ROOT));
        boolean success = item != null && FourthRoomGameManager.of(source.getLevel()).buyItem(player.getUUID(), item);
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.buy.success" : "command.fourth_room.buy.failure"), false);
        return success ? 1 : 0;
    }

    private static int useItem(CommandSourceStack source, String itemId, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FourthRoomShopItem item = FourthRoomShopItem.byId(itemId.toLowerCase(Locale.ROOT));
        boolean success = item != null && FourthRoomGameManager.of(source.getLevel()).useAssassinationItem(player.getUUID(), target.getUUID(), item);
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.use_item.success" : "command.fourth_room.use_item.failure"), false);
        return success ? 1 : 0;
    }

    private static int completeTask(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).taskScheduler().completeTask(player.getUUID());
        source.sendSuccess(() -> Component.translatable(success ? "command.fourth_room.complete_task.success" : "command.fourth_room.complete_task.failure"), false);
        return success ? 1 : 0;
    }

    private static int searchNotes(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> notes = FourthRoomGameManager.of(source.getLevel()).searchNotes(player.getUUID());
        source.sendSuccess(() -> Component.translatable(notes.isEmpty() ? "command.fourth_room.search_notes.none" : "command.fourth_room.search_notes.found", String.join(" | ", notes)), false);
        return notes.size();
    }
}