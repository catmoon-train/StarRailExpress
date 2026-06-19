package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.network.OpenRoleRosterScreenPayload;
import io.wifi.starrailexpress.roster.RoleRosterManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 职业轮换系统指令：
 * <ul>
 *     <li>{@code /sre:roster} —— 打开玩家查看界面（任意玩家）。</li>
 *     <li>{@code /sre:roster edit} —— 打开管理员编辑界面（OP）。</li>
 *     <li>{@code /sre:roster enable|disable} —— 开关名单是否接管职业分配（OP）。</li>
 *     <li>{@code /sre:roster randomize [players]} —— 随机抽选生成名单（OP）。</li>
 *     <li>{@code /sre:roster status} —— 查看当前状态（OP）。</li>
 * </ul>
 */
public final class RoleRosterCommand {
    private RoleRosterCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sre:roster")
                .executes(ctx -> openScreen(ctx, false))
                .then(Commands.literal("edit")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> openScreen(ctx, true)))
                .then(Commands.literal("enable")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> setEnabled(ctx, true)))
                .then(Commands.literal("disable")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> setEnabled(ctx, false)))
                .then(Commands.literal("randomize")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> randomize(ctx, ctx.getSource().getServer().getPlayerCount()))
                        .then(Commands.argument("players", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> randomize(ctx, IntegerArgumentType.getInteger(ctx, "players")))))
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(RoleRosterCommand::status)));
    }

    private static int openScreen(CommandContext<CommandSourceStack> ctx, boolean admin) {
        ServerPlayer player;
        try {
            player = ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("commands.sre.role_roster.not_player"));
            return 0;
        }
        ServerPlayNetworking.send(player, new OpenRoleRosterScreenPayload(admin));
        return 1;
    }

    private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
        RoleRosterManager.setEnabled(enabled);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                enabled ? "commands.sre.role_roster.enabled" : "commands.sre.role_roster.disabled"), true);
        return 1;
    }

    private static int randomize(CommandContext<CommandSourceStack> ctx, int players) {
        RoleRosterManager.randomize(Math.max(1, players));
        int size = RoleRosterManager.getState().roleCounts.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.role_roster.randomized", size), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        var state = RoleRosterManager.getState();
        ctx.getSource().sendSuccess(() -> Component.translatable("commands.sre.role_roster.status",
                state.enabled ? "ON" : "OFF", state.roleCounts.size()), false);
        return 1;
    }
}
