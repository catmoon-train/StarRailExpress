package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteSession;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code sre:vote} 指令 – 管理自定义投票会话。
 *
 * <pre>
 * sre:vote list                              – 列出所有 pending/active 会话
 * sre:vote create &lt;id&gt; &lt;title&gt;              – 创建一个 pending 投票 Builder
 * sre:vote &lt;id&gt; add text &lt;text&gt;             – 添加文本选项
 * sre:vote &lt;id&gt; add player &lt;player&gt;         – 以玩家为选项
 * sre:vote &lt;id&gt; add item                    – 以执行者手持物品为选项
 * sre:vote &lt;id&gt; set showresults &lt;bool&gt;      – 是否显示实时票数
 * sre:vote &lt;id&gt; set allowrevote &lt;bool&gt;      – 是否允许重投
 * sre:vote &lt;id&gt; set refresh &lt;ticks&gt;         – 刷新间隔（ticks）
 * sre:vote &lt;id&gt; start [seconds]             – 构建并启动投票
 * sre:vote &lt;id&gt; stop                        – 强制结束活跃投票
 * sre:vote &lt;id&gt; status                      – 查看当前票数
 * sre:vote &lt;id&gt; remove                      – 删除 pending Builder
 * </pre>
 */
public class VoteCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("sre:vote")
                .requires(src -> src.hasPermission(2))

                // sre:vote list
                .then(Commands.literal("list")
                    .executes(ctx -> listSessions(ctx.getSource())))

                // sre:vote create <id> <title>
                .then(Commands.literal("create")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("title", StringArgumentType.greedyString())
                            .executes(ctx -> createSession(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                StringArgumentType.getString(ctx, "title"))))))

                // sre:vote <id> ...
                .then(Commands.argument("id", StringArgumentType.word())

                    // add
                    .then(Commands.literal("add")

                        // add text <text>
                        .then(Commands.literal("text")
                            .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> addText(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    StringArgumentType.getString(ctx, "text")))))

                        // add player <player>
                        .then(Commands.literal("player")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> addPlayer(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    EntityArgument.getPlayer(ctx, "player")))))

                        // add item  (使用执行者手中物品)
                        .then(Commands.literal("item")
                            .executes(ctx -> addItem(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))))

                    // set
                    .then(Commands.literal("set")

                        .then(Commands.literal("showresults")
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setShowResults(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    BoolArgumentType.getBool(ctx, "value")))))

                        .then(Commands.literal("allowrevote")
                            .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> setAllowRevote(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    BoolArgumentType.getBool(ctx, "value")))))

                        .then(Commands.literal("refresh")
                            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(ctx -> setRefresh(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "id"),
                                    IntegerArgumentType.getInteger(ctx, "ticks"))))))

                    // start [seconds]
                    .then(Commands.literal("start")
                        .executes(ctx -> startSession(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"),
                            60))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1))
                            .executes(ctx -> startSession(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "seconds")))))

                    // stop
                    .then(Commands.literal("stop")
                        .executes(ctx -> stopSession(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"))))

                    // status
                    .then(Commands.literal("status")
                        .executes(ctx -> showStatus(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"))))

                    // remove (pending only)
                    .then(Commands.literal("remove")
                        .executes(ctx -> removeSession(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "id"))))
                )
        );
    }

    // ── 指令实现 ──────────────────────────────────────────────────────────────

    private static int listSessions(CommandSourceStack src) {
        VoteManager mgr = VoteManager.getInstance();
        src.sendSuccess(() -> Component.literal("=== 投票会话列表 ==="), false);

        boolean any = false;
        for (VoteSession s : mgr.getActiveSessions()) {
            long rem = s.getEndTimeMillis() > 0 ? (s.getEndTimeMillis() - System.currentTimeMillis()) / 1000 : -1;
            src.sendSuccess(() -> Component.literal(
                String.format("  [活跃] %s | %s | 剩余 %ss | 已投 %d 票",
                    s.getId(), s.getTitle(),
                    rem < 0 ? "∞" : rem,
                    s.getTotalVotes())), false);
            any = true;
        }
        for (String pid : mgr.getPendingIds()) {
            VoteSession.Builder b = mgr.getPendingBuilder(pid);
            if (b != null) {
                src.sendSuccess(() -> Component.literal(
                    String.format("  [等待] %s | %s | %d 个选项",
                        pid, b.getTitle(), b.getOptionCount())), false);
                any = true;
            }
        }
        if (!any) src.sendSuccess(() -> Component.literal("  （无）"), false);
        return 1;
    }

    private static int createSession(CommandSourceStack src, String id, String title) {
        VoteManager mgr = VoteManager.getInstance();
        if (mgr.hasPending(id) || mgr.getActive(id) != null) {
            src.sendFailure(Component.literal("会话 '" + id + "' 已存在"));
            return 0;
        }
        mgr.createPending(id, title);
        src.sendSuccess(() -> Component.literal(
            "✔ 已创建投票 '" + id + "'（标题：" + title + "）。\n" +
            "使用 sre:vote " + id + " add text/player/item 添加选项，\n" +
            "然后 sre:vote " + id + " start [秒] 启动投票。"), false);
        return 1;
    }

    private static int addText(CommandSourceStack src, String id, String text) {
        return withPendingBuilder(src, id, b -> {
            b.addTextOption(text);
            src.sendSuccess(() -> Component.literal("✔ 已添加文本选项：" + text), false);
        });
    }

    private static int addPlayer(CommandSourceStack src, String id, ServerPlayer player) {
        return withPendingBuilder(src, id, b -> {
            b.addPlayerOption(player);
            src.sendSuccess(() -> Component.literal("✔ 已添加玩家选项：" + player.getName().getString()), false);
        });
    }

    private static int addItem(CommandSourceStack src, String id) {
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("该指令需要玩家执行（用于获取手持物品）"));
            return 0;
        }
        var item = player.getMainHandItem();
        if (item.isEmpty()) {
            src.sendFailure(Component.literal("手中没有物品"));
            return 0;
        }
        return withPendingBuilder(src, id, b -> {
            b.addItemOption(item);
            src.sendSuccess(() -> Component.literal("✔ 已添加物品选项：" + item.getHoverName().getString()), false);
        });
    }

    private static int setShowResults(CommandSourceStack src, String id, boolean value) {
        return withPendingBuilder(src, id, b -> {
            b.showResults(value);
            src.sendSuccess(() -> Component.literal("✔ showResults = " + value), false);
        });
    }

    private static int setAllowRevote(CommandSourceStack src, String id, boolean value) {
        return withPendingBuilder(src, id, b -> {
            b.allowRevote(value);
            src.sendSuccess(() -> Component.literal("✔ allowRevote = " + value), false);
        });
    }

    private static int setRefresh(CommandSourceStack src, String id, int ticks) {
        return withPendingBuilder(src, id, b -> {
            b.refreshIntervalTicks(ticks);
            src.sendSuccess(() -> Component.literal("✔ refreshIntervalTicks = " + ticks), false);
        });
    }

    private static int startSession(CommandSourceStack src, String id, int seconds) {
        VoteManager mgr = VoteManager.getInstance();
        if (mgr.getActive(id) != null) {
            src.sendFailure(Component.literal("投票 '" + id + "' 已在运行中"));
            return 0;
        }
        VoteSession.Builder builder = mgr.getPendingBuilder(id);
        if (builder == null) {
            src.sendFailure(Component.literal("找不到 pending 会话 '" + id + "'，请先 create"));
            return 0;
        }
        if (builder.getOptionCount() == 0) {
            src.sendFailure(Component.literal("至少需要一个选项才能启动投票，请使用 add 添加"));
            return 0;
        }
        builder.durationSeconds(seconds);
        boolean started = mgr.start(id, src.getServer());
        if (!started) {
            src.sendFailure(Component.literal("启动失败（内部错误）"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("✔ 投票 '" + id + "' 已启动，持续 " + seconds + " 秒"), true);
        return 1;
    }

    private static int stopSession(CommandSourceStack src, String id) {
        boolean stopped = VoteManager.getInstance().stop(id, src.getServer());
        if (!stopped) {
            src.sendFailure(Component.literal("找不到活跃的投票 '" + id + "'"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("✔ 投票 '" + id + "' 已强制结束"), true);
        return 1;
    }

    private static int showStatus(CommandSourceStack src, String id) {
        VoteSession session = VoteManager.getInstance().getActive(id);
        if (session == null) {
            src.sendFailure(Component.literal("找不到活跃的投票 '" + id + "'"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("=== 投票 '" + id + "' 状态 ==="), false);
        src.sendSuccess(() -> Component.literal("标题: " + session.getTitle()), false);
        long rem = session.getEndTimeMillis() > 0
                ? (session.getEndTimeMillis() - System.currentTimeMillis()) / 1000 : -1;
        src.sendSuccess(() -> Component.literal("剩余: " + (rem < 0 ? "∞" : rem + "s")), false);
        src.sendSuccess(() -> Component.literal("总票数: " + session.getTotalVotes()), false);
        for (var entry : session.getVoteCounts().entrySet()) {
            int optIdx = entry.getKey();
            int count = entry.getValue();
            String label = session.getOptions().get(optIdx).getLabel();
            src.sendSuccess(() -> Component.literal("  [" + optIdx + "] " + label + ": " + count + " 票"), false);
        }
        return 1;
    }

    private static int removeSession(CommandSourceStack src, String id) {
        boolean removed = VoteManager.getInstance().removePending(id);
        if (!removed) {
            src.sendFailure(Component.literal("找不到 pending 会话 '" + id + "'（活跃中的投票请使用 stop）"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("✔ 已删除 pending 会话 '" + id + "'"), false);
        return 1;
    }

    // ── 辅助 ──────────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface BuilderAction {
        void accept(VoteSession.Builder builder);
    }

    private static int withPendingBuilder(CommandSourceStack src, String id, BuilderAction action) {
        VoteManager mgr = VoteManager.getInstance();
        VoteSession.Builder builder = mgr.getPendingBuilder(id);
        if (builder == null) {
            if (mgr.getActive(id) != null) {
                src.sendFailure(Component.literal("投票 '" + id + "' 已在活跃中，无法修改配置"));
            } else {
                src.sendFailure(Component.literal("找不到 pending 会话 '" + id + "'，请先 create"));
            }
            return 0;
        }
        action.accept(builder);
        return 1;
    }
}
