package org.agmas.noellesroles.game.modes.echo.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultSnapshot;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;

import java.util.Locale;

/** 固定管理入口；当前地图预检不接收自由路径，避免命令参数截断与目录越界。 */
public final class EchoCommand {
    private EchoCommand() { }

    public static void register() {
        EchoMapCommand.initialize();
        EchoSwitchMapCommand.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:echo").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("abort").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.VOID)))
                        .then(EchoMapCommand.tree())
                        .then(EchoSwitchMapCommand.tree())
                        .then(Commands.literal("reload").executes(ctx -> EchoMapTools.reload(ctx.getSource())))
                        .then(Commands.literal("debug").requires(source -> Boolean.getBoolean("sre.echo.development"))
                                .then(Commands.literal("evacuation").executes(ctx -> evacuation(ctx.getSource())))
                                .then(Commands.literal("carriers_win").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.CARRIERS)))
                                .then(Commands.literal("interceptors_win").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.INTERCEPTORS))))));
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> dispatcher.register(
                Commands.literal("sre:echo_drop").requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                        .then(Commands.literal("cargo").executes(ctx -> drop(ctx.getSource(),false)))
                        .then(Commands.literal("key").executes(ctx -> drop(ctx.getSource(),true)))));
        // 职业切换：只改打法，不改专长；SAFE 之外一律拒绝并说明原因。
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> {
            var all=org.agmas.noellesroles.game.modes.echo.classes.EchoClass.values();
            var root=Commands.literal("sre:echo_class")
                    .requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                    .executes(ctx -> currentClass(ctx.getSource()));
            for(int n=0;n<all.length;n++) {
                final int option=n;
                root.then(Commands.literal(all[n].id()).executes(ctx -> selectClass(ctx.getSource(),option)));
            }
            dispatcher.register(root);
        });
    }

    private static int status(CommandSourceStack source) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null) return missing(source);
        source.sendSuccess(() -> Component.translatable("echo.command.status", match.id().toString(),
                Component.translatable("echo.phase." + match.phase().name().toLowerCase(Locale.ROOT)),
                match.matchTick(), match.playerCount()), false);
        return 1;
    }

    private static int finish(CommandSourceStack source, EchoResultSnapshot.Outcome outcome) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null || match.result() != null) return missing(source);
        if(outcome!=EchoResultSnapshot.Outcome.VOID) EchoSessions.markDebug(source.getLevel());
        match.settle(outcome, outcome == EchoResultSnapshot.Outcome.VOID ? "ADMIN_ABORT" : "DEBUG_RESULT", true);
        source.sendSuccess(() -> Component.translatable("echo.command.completed"), true);
        return 1;
    }

    private static int evacuation(CommandSourceStack source) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null || !match.requestDevelopmentEvacuation()) return missing(source);
        EchoSessions.markDebug(source.getLevel());
        source.sendSuccess(() -> Component.translatable("echo.command.completed"), false);
        return 1;
    }

    /** 切换职业；无参数形式回显当前职业，方便确认默认分配。 */
    private static int selectClass(CommandSourceStack source,int option) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return missing(source);
        var match=EchoSessions.get(source.getLevel());
        if(match==null || !match.selectClass(player,option)) {
            source.sendFailure(Component.translatable("echo.class.locked"));
            return 0;
        }
        var current=match.playerClass(player.getUUID());
        source.sendSuccess(() -> Component.translatable("echo.class.selected",
                Component.translatable(current.nameKey())),false);
        return 1;
    }

    private static int currentClass(CommandSourceStack source) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return missing(source);
        var match=EchoSessions.get(source.getLevel());
        var current=match==null?null:match.playerClass(player.getUUID());
        if(current==null) {
            source.sendFailure(Component.translatable("echo.class.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("echo.class.selected",Component.translatable(current.nameKey())),false);
        return 1;
    }

    private static int drop(CommandSourceStack source,boolean key) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return missing(source);
        var match=EchoSessions.get(source.getLevel());
        if(match==null || !match.drop(player,key)) return missing(source);
        return 1;
    }
    private static int missing(CommandSourceStack source) {
        source.sendFailure(Component.translatable("echo.error.no_active_session"));
        return 0;
    }
}
