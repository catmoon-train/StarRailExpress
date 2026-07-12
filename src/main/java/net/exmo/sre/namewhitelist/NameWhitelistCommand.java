package net.exmo.sre.namewhitelist;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.exmo.sre.mod_whitelist.common.utils.MWLogger;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 用户名(username)白名单命令 + 玩家加入拦截。
 * <p>
 * 使用 {@code ;} 分隔一键批量导入。命令根节点 {@code /namewl} 需要 OP(权限等级 3)。
 * <p>
 * 拦截采用与 {@link net.exmo.sre.mod_whitelist} 一致的“进入游戏阶段后再断开”方案
 * （{@link ServerPlayConnectionEvents#JOIN}），而非 handshake 阶段的 mixin——
 * 这是本项目既有的白名单校验方式（见 mod_whitelist 中已废弃的 handshake mixin 注释），
 * 可避免语音/代理相关的兼容问题，也符合 ai_doc.md “优先使用 Event 而非 mixin”的要求。
 */
public final class NameWhitelistCommand {

    /** 防止在 CommandRegistrationCallback 多次触发时重复注册 JOIN 监听。 */
    private static boolean joinListenerRegistered = false;

    private NameWhitelistCommand() {
    }

    public static void register() {
        NameWhitelistStorage.init();
        registerJoinGate();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("namewl")
                        .requires(source -> source.hasPermission(3)) // 仅 OP
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(NameWhitelistCommand::add)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(NameWhitelistCommand::remove)))
                        // 一键批量导入：用 ; 分隔多个用户名
                        .then(Commands.literal("import")
                                .then(Commands.argument("names", StringArgumentType.greedyString())
                                        .executes(NameWhitelistCommand::importNames)))
                        .then(Commands.literal("list")
                                .executes(NameWhitelistCommand::list))
                        .then(Commands.literal("clear")
                                .executes(NameWhitelistCommand::clear))
                        .then(Commands.literal("on")
                                .executes(ctx -> setEnabled(ctx, true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setEnabled(ctx, false)))
                        .then(Commands.literal("reload")
                                .executes(NameWhitelistCommand::reload))
                        .then(Commands.literal("status")
                                .executes(NameWhitelistCommand::status))));
    }

    /** 注册玩家加入拦截：启用时，非白名单且非 OP 的玩家将被断开。 */
    private static void registerJoinGate() {
        if (joinListenerRegistered) return;
        joinListenerRegistered = true;

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!NameWhitelistStorage.isEnabled()) return;

            ServerPlayer player = handler.player;
            // OP 始终放行，避免管理员把自己锁在门外。
            if (player.hasPermissions(2)) return;

            String username = player.getGameProfile().getName();
            if (!NameWhitelistStorage.isWhitelisted(username)) {
                MWLogger.LOGGER.info("[NameWhitelist] 拒绝玩家 {}（不在用户名白名单中）", username);
                player.connection.disconnect(Component.literal("§c你不在白名单中，无法进入服务器。\n§7请联系管理员将你的游戏名加入白名单。"));
            }
        });
    }

    private static int add(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean added = NameWhitelistStorage.add(name);
        if (added) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a已将 §f" + name + " §a加入白名单。"), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("§e" + name + " §7已在白名单中。"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        boolean removed = NameWhitelistStorage.remove(name);
        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a已将 §f" + name + " §a移出白名单。"), true);
        } else {
            ctx.getSource().sendFailure(Component.literal("§7" + name + " 不在白名单中。"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int importNames(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "names");
        int[] result = NameWhitelistStorage.importNames(raw);
        int added = result[0];
        int skipped = result[1];
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a批量导入完成：新增 §f" + added + " §a个，跳过(重复) §f" + skipped + " §a个。当前共 §f"
                        + NameWhitelistStorage.size() + " §a个。"), true);
        return added;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        List<String> names = NameWhitelistStorage.listNames();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6用户名白名单（" + names.size() + " 个，状态：" + (NameWhitelistStorage.isEnabled() ? "§a启用" : "§c关闭") + "§6）:"), false);
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7（空）"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§f" + String.join("§7, §f", names)), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        int removed = NameWhitelistStorage.clear();
        ctx.getSource().sendSuccess(() -> Component.literal("§a已清空白名单，共移除 §f" + removed + " §a个名字。"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean value) {
        NameWhitelistStorage.setEnabled(value);
        ctx.getSource().sendSuccess(() -> Component.literal(
                value ? "§a用户名白名单已§f启用§a。非白名单玩家(OP 除外)将被拒绝进入。"
                        : "§e用户名白名单已§f关闭§e。所有玩家均可进入。"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        NameWhitelistStorage.reload();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§a已从磁盘重新加载白名单：共 §f" + NameWhitelistStorage.size() + " §a个，状态："
                        + (NameWhitelistStorage.isEnabled() ? "§a启用" : "§c关闭")), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6用户名白名单状态：" + (NameWhitelistStorage.isEnabled() ? "§a启用" : "§c关闭")
                        + " §6| 名字数量：§f" + NameWhitelistStorage.size()), false);
        return Command.SINGLE_SUCCESS;
    }
}
