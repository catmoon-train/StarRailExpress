package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.api.PlushApi;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.SREFumoBlocks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 皮肤玩偶发放指令（OP，权限等级 2）。
 * <ul>
 *     <li>{@code /sre:plush get <skin> [target]} —— 把 {@code <skin>} 对应的 plush 发给目标玩家（缺省为执行者本人）。</li>
 *     <li>{@code /sre:plush list} —— 列出所有可用的皮肤名（即当前已注册的 plush）。</li>
 * </ul>
 * 映射逻辑见 {@link PlushApi}：皮肤名 {@code X} → 物品 {@code noellesroles:X_plush}。
 * 不新建任何模型/贴图，仅复用仓库中现成的 plush。
 */
public final class PlushCommand {
    private PlushCommand() {
    }

    /** {@code <skin>} 参数的补全：建议当前所有可用的皮肤名。 */
    private static final SuggestionProvider<CommandSourceStack> SKIN_SUGGESTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(PlushApi.availableSkinNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:plush")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(PlushCommand::list))
                .then(Commands.literal("get")
                        .then(Commands.argument("skin", StringArgumentType.word())
                                .suggests(SKIN_SUGGESTIONS)
                                .executes(ctx -> giveSelf(ctx, StringArgumentType.getString(ctx, "skin")))
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(ctx -> giveTargets(ctx, StringArgumentType.getString(ctx, "skin"))))))
                .then(Commands.literal("player")
                        .then(Commands.argument("username", StringArgumentType.word())
                                .executes(ctx -> givePlayerPlush(ctx, StringArgumentType.getString(ctx, "username"),
                                        null))
                                .then(Commands.argument("target", EntityArgument.players())
                                        .executes(ctx -> givePlayerPlush(ctx,
                                                StringArgumentType.getString(ctx, "username"), "target"))))));
    }

    /** 发放绑定指定玩家名的自定义玩家 plush（按该玩家的皮肤渲染）。 */
    private static int givePlayerPlush(CommandContext<CommandSourceStack> ctx, String username, String targetArg) {
        Collection<ServerPlayer> targets;
        if (targetArg != null) {
            try {
                targets = EntityArgument.getPlayers(ctx, targetArg);
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_player"));
                return 0;
            }
        } else {
            ServerPlayer self;
            try {
                self = ctx.getSource().getPlayerOrException();
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_player"));
                return 0;
            }
            targets = List.of(self);
        }

        Item plushItem = SREFumoBlocks.CUSTOM_PLAYER_PLUSH.asItem();
        for (ServerPlayer target : targets) {
            ItemStack stack = new ItemStack(plushItem);
            stack.set(SREDataComponentTypes.PLUSH_PLAYER, username);
            if (!target.addItem(stack) && !stack.isEmpty()) {
                target.drop(stack, false);
            }
        }
        if (targets.size() == 1) {
            ServerPlayer only = targets.iterator().next();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "commands.sre.plush.player_given", username, only.getDisplayName()), true);
        } else {
            int count = targets.size();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "commands.sre.plush.player_given_multiple", username, count), true);
        }
        return targets.size();
    }

    private static int giveSelf(CommandContext<CommandSourceStack> ctx, String skin) {
        ServerPlayer self;
        try {
            self = ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_player"));
            return 0;
        }
        return giveTo(ctx, skin, List.of(self));
    }

    private static int giveTargets(CommandContext<CommandSourceStack> ctx, String skin) throws CommandSyntaxException {
        return giveTo(ctx, skin, EntityArgument.getPlayers(ctx, "target"));
    }

    private static int giveTo(CommandContext<CommandSourceStack> ctx, String skin, Collection<ServerPlayer> targets) {
        Optional<Item> plush = PlushApi.getPlushForSkin(skin);
        if (plush.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_found", skin));
            return 0;
        }
        Item item = plush.get();
        for (ServerPlayer target : targets) {
            ItemStack stack = new ItemStack(item);
            // 背包放不下时掉落到地面，避免静默丢失
            if (!target.addItem(stack) && !stack.isEmpty()) {
                target.drop(stack, false);
            }
        }
        Component plushName = new ItemStack(item).getHoverName();
        if (targets.size() == 1) {
            ServerPlayer only = targets.iterator().next();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "commands.sre.plush.given", plushName, only.getDisplayName()), true);
        } else {
            int count = targets.size();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "commands.sre.plush.given_multiple", plushName, count), true);
        }
        return targets.size();
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = PlushApi.availableSkinNames();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "commands.sre.plush.list", names.size(), String.join(", ", names)), false);
        return names.size();
    }
}
