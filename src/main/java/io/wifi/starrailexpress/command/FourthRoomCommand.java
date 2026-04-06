package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.fourthroom.card.CardRegistry;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomGameManager;
import io.wifi.starrailexpress.fourthroom.game.FourthRoomPlayerState;
import io.wifi.starrailexpress.fourthroom.shop.FourthRoomShopItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
        source.sendSuccess(() -> Component.literal("Fourth Room phase=" + data.phase.name() + ", activeTask=" + data.activeTaskId
                + ", nextRotationTick=" + data.nextRotationTick + ", rooms=" + data.rooms.size()), false);
        try {
            ServerPlayer player = source.getPlayerOrException();
            FourthRoomPlayerState state = data.players.get(player.getUUID());
            if (state != null) {
                List<String> hand = state.hand.stream().map(card -> card.cardId() + (card.gold() ? "*" : "")).toList();
                source.sendSuccess(() -> Component.literal("Your room=" + state.roomId + ", coins=" + state.coins + ", hand=" + hand), false);
            }
        } catch (Exception ignored) {
        }
        return 1;
    }

    private static int reveal(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).revealOwnIdentity(player.getUUID());
        source.sendSuccess(() -> Component.literal(success ? "Identity revealed." : "No hidden identity left."), false);
        return success ? 1 : 0;
    }

    private static int play(CommandSourceStack source, String cardId, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).playCard(player.getUUID(), cardId, target == null ? null : target.getUUID());
        source.sendSuccess(() -> Component.literal(success ? "Card played." : "Card play failed."), false);
        return success ? 1 : 0;
    }

    private static int endTurn(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).endTurn(player.getUUID());
        source.sendSuccess(() -> Component.literal(success ? "Turn ended." : "Cannot end turn now."), false);
        return success ? 1 : 0;
    }

    private static int buy(CommandSourceStack source, String itemId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FourthRoomShopItem item = FourthRoomShopItem.byId(itemId.toLowerCase(Locale.ROOT));
        boolean success = item != null && FourthRoomGameManager.of(source.getLevel()).buyItem(player.getUUID(), item);
        source.sendSuccess(() -> Component.literal(success ? "Purchase complete." : "Purchase failed."), false);
        return success ? 1 : 0;
    }

    private static int useItem(CommandSourceStack source, String itemId, ServerPlayer target) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FourthRoomShopItem item = FourthRoomShopItem.byId(itemId.toLowerCase(Locale.ROOT));
        boolean success = item != null && FourthRoomGameManager.of(source.getLevel()).useAssassinationItem(player.getUUID(), target.getUUID(), item);
        source.sendSuccess(() -> Component.literal(success ? "Item used." : "Item use failed."), false);
        return success ? 1 : 0;
    }

    private static int completeTask(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean success = FourthRoomGameManager.of(source.getLevel()).taskScheduler().completeTask(player.getUUID());
        source.sendSuccess(() -> Component.literal(success ? "Task marked complete." : "No active task to complete."), false);
        return success ? 1 : 0;
    }

    private static int searchNotes(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<String> notes = FourthRoomGameManager.of(source.getLevel()).searchNotes(player.getUUID());
        source.sendSuccess(() -> Component.literal(notes.isEmpty() ? "No notes found." : String.join(" | ", notes)), false);
        return notes.size();
    }
}