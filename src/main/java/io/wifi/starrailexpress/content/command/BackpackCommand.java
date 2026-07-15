package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.backpack.BackpackManager;
import io.wifi.starrailexpress.network.OpenBackpackScreenPayload;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /sre:backpack} —— 打开场外背包 GUI。沿用代码库冒号字面量命令约定（同 {@code sre:pass}）。
 * <p>
 * {@code /sre:backpack card <add|set|remove|get> <targets> <type> [count]} —— 管理场外背包阵营卡（OP）。
 * 全部走 {@link BackpackManager} 公开 API：{@code set} 通过差值调用 {@code addCard} 实现，不新增 Manager 方法。
 */
public final class BackpackCommand {
    private BackpackCommand() {
    }

    /** 卡牌类型自动补全：列出除 {@link FactionCardType#NONE} 外的所有类型键。 */
    private static final SuggestionProvider<CommandSourceStack> CARD_TYPES = (context, builder) -> {
        for (FactionCardType type : FactionCardType.values()) {
            if (type != FactionCardType.NONE) {
                builder.suggest(type.questKey);
            }
        }
        return builder.buildFuture();
    };

    private enum Op { ADD, SET, REMOVE }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:backpack")
                .executes(context -> open(context.getSource()))
                .then(Commands.literal("card")
                        .requires(source -> source.hasPermission(2))
                        .then(countBranch("add", 1, Op.ADD))
                        .then(countBranch("remove", 1, Op.REMOVE))
                        .then(countBranch("set", 0, Op.SET))
                        .then(Commands.literal("get")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests(CARD_TYPES)
                                                .executes(BackpackCommand::get))))));
    }

    /** 构造 {@code <literal> <targets> <type> <count>} 这一支子命令。 */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> countBranch(
            String literal, int minCount, Op op) {
        return Commands.literal(literal)
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(CARD_TYPES)
                                .then(Commands.argument("count", IntegerArgumentType.integer(minCount))
                                        .executes(context -> apply(context, op)))));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // 开屏前重发一次背包分区，保证客户端展示的是最新计数
        BackpackManager.resend(player);
        ServerPlayNetworking.send(player, new OpenBackpackScreenPayload());
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> context, Op op) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        FactionCardType type = parseType(context, source);
        if (type == null) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        int count = IntegerArgumentType.getInteger(context, "count");

        int affected = 0;
        ServerPlayer last = null;
        for (ServerPlayer player : targets) {
            if (!ensureLoaded(source, player)) {
                continue;
            }
            switch (op) {
                case ADD -> BackpackManager.addCard(player, type, count);
                case REMOVE -> BackpackManager.addCard(player, type, -count);
                case SET -> BackpackManager.addCard(player, type, count - BackpackManager.getCardCount(player, type));
            }
            affected++;
            last = player;
        }
        if (affected == 0) {
            return 0;
        }

        Component cardName = Component.translatable(type.displayName);
        String base = "commands.sre.backpack.card." + op.name().toLowerCase(java.util.Locale.ROOT);
        if (affected == 1) {
            ServerPlayer only = last;
            int now = BackpackManager.getCardCount(only, type);
            source.sendSuccess(() -> Component.translatable(base,
                    only.getName().getString(), count, cardName, now)
                    .withStyle(style -> style.withColor(0x00FF00)), true);
        } else {
            int total = affected;
            source.sendSuccess(() -> Component.translatable(base + ".multiple", total, count, cardName)
                    .withStyle(style -> style.withColor(0x00FF00)), true);
        }
        return affected;
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        FactionCardType type = parseType(context, source);
        if (type == null) {
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        Component cardName = Component.translatable(type.displayName);

        if (targets.size() == 1) {
            ServerPlayer only = targets.iterator().next();
            int count = BackpackManager.getCardCount(only, type);
            source.sendSuccess(() -> Component.translatable("commands.sre.backpack.card.get",
                    only.getName().getString(), count, cardName)
                    .withStyle(style -> style.withColor(0x00FF00)), false);
            return count;
        }

        int total = 0;
        for (ServerPlayer player : targets) {
            total += BackpackManager.getCardCount(player, type);
        }
        int sum = total;
        source.sendSuccess(() -> Component.translatable("commands.sre.backpack.card.get.multiple",
                targets.size(), sum, cardName)
                .withStyle(style -> style.withColor(0x00FF00)), false);
        return total;
    }

    private static FactionCardType parseType(CommandContext<CommandSourceStack> context, CommandSourceStack source) {
        String raw = StringArgumentType.getString(context, "type");
        FactionCardType type = FactionCardType.fromString(raw);
        if (type == FactionCardType.NONE) {
            source.sendFailure(Component.translatable("commands.sre.backpack.card.invalid_type", raw));
            return null;
        }
        return type;
    }

    /** DB 同步开启时，玩家背包分区尚未加载完就写入会被随后完成的异步加载覆盖，故先拦下。 */
    private static boolean ensureLoaded(CommandSourceStack source, ServerPlayer player) {
        if (BackpackManager.isLoaded(player.getUUID())) {
            return true;
        }
        source.sendFailure(Component.translatable("commands.sre.backpack.card.not_loaded",
                player.getName().getString()));
        return false;
    }
}
