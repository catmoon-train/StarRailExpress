package net.exmo.sre.client.chat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

/**
 * 聊天对话指令。
 * <p>
 * 用法：
 * <ul>
 *   <li>{@code /sre:chat open <dialogueId> <players>} — 对指定玩家打开对话</li>
 *   <li>{@code /sre:chat open <dialogueId> <players> <entity>} — 打开对话 + Camera 聚焦到实体</li>
 *   <li>{@code /sre:chat reload} — 重新加载对话配置文件</li>
 *   <li>{@code /sre:chat list} — 列出所有已加载的对话 ID</li>
 * </ul>
 */
public class ChatDialogueCommand {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_DIALOGUES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    ChatDialogueManager.getInstance(ctx.getSource().getServer()).getAll().keySet(),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:chat")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("open")
                                .then(Commands.argument("dialogueId", StringArgumentType.word())
                                        .suggests(SUGGEST_DIALOGUES)
                                        .then(Commands.argument("players", EntityArgument.players())
                                                // /sre:chat open <id> <players>
                                                .executes(ctx -> executeOpen(ctx, -1))
                                                // /sre:chat open <id> <players> <entity>
                                                .then(Commands.argument("focusEntity", EntityArgument.entity())
                                                        .executes(ctx -> {
                                                            Entity entity = EntityArgument.getEntity(ctx, "focusEntity");
                                                            return executeOpen(ctx, entity.getId());
                                                        })))))
                        .then(Commands.literal("reload")
                                .executes(ctx -> executeReload(ctx.getSource())))
                        .then(Commands.literal("list")
                                .executes(ctx -> executeList(ctx.getSource()))));
    }

    private static int executeOpen(CommandContext<CommandSourceStack> ctx, int focusEntityId) {
        String dialogueId = StringArgumentType.getString(ctx, "dialogueId");
        Collection<ServerPlayer> players;
        try {
            players = EntityArgument.getPlayers(ctx, "players");
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Invalid player selector"));
            return 0;
        }

        ChatDialogueData data = ChatDialogueManager.getInstance(
                ctx.getSource().getServer()).get(dialogueId);

        if (data == null) {
            ctx.getSource().sendFailure(
                    Component.literal("[SRE-Chat] Dialogue not found: " + dialogueId)
                            .withStyle(s -> s.withColor(0xFF5555)));
            return 0;
        }

        for (ServerPlayer player : players) {
            OpenChatDialoguePayload.sendToPlayer(player, data, focusEntityId);
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("[SRE-Chat] Opened dialogue '" + dialogueId
                        + "' for " + players.size() + " player(s)")
                        .withStyle(s -> s.withColor(0x55FF55)),
                true);
        return players.size();
    }

    private static int executeReload(CommandSourceStack source) {
        ChatDialogueManager.reset();
        ChatDialogueManager.getInstance(source.getServer());
        source.sendSuccess(
                () -> Component.literal("[SRE-Chat] Dialogue configs reloaded")
                        .withStyle(s -> s.withColor(0x55FF55)),
                true);
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        var all = ChatDialogueManager.getInstance(source.getServer()).getAll();
        if (all.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("[SRE-Chat] No dialogues loaded"),
                    false);
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("[SRE-Chat] " + all.size() + " dialogue(s):"),
                false);
        for (var entry : all.entrySet()) {
            String id = entry.getKey();
            String title = entry.getValue().title;
            int lineCount = entry.getValue().lines.size();
            source.sendSuccess(
                    () -> Component.literal("  - " + id + " (\"" + title + "\", " + lineCount + " lines)")
                            .withStyle(s -> s.withColor(0xAABBCC)),
                    false);
        }
        return all.size();
    }
}
