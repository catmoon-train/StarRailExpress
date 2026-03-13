package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.SRE;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;

public class ChangeRoleCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("changeRole")
                .requires(serverCommandSource -> serverCommandSource.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("role", RoleArgumentType.create())
                                .executes(ChangeRoleCommand::execute))));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        try {
            if (!Harpymodloader.isMojangVerify) {
                return 1;
            }
            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
            Role newRole = RoleArgumentType.getRole(context, "role");

            // 获取游戏世界组件
            StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(targetPlayer.level());

            // 获取玩家当前角色
            Role oldRole = gameWorldComponent.getRole(targetPlayer);

            // 移除旧角色事件
            if (oldRole != null) {
                ModdedRoleRemoved.EVENT.invoker().removeModdedRole(targetPlayer, oldRole);
            }

            // 分配新角色
            gameWorldComponent.addRole(targetPlayer, newRole);

            // 触发新角色分配事件
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(targetPlayer, newRole);

            if (gameWorldComponent.isRunning()) {
                SRE.REPLAY_MANAGER.recordPlayerRoleChange(targetPlayer.getUUID(), oldRole, newRole);
            }

            // 发送反馈消息
            final MutableComponent newRoleText = Harpymodloader.getRoleName(newRole).withColor(newRole.color())
                    .withStyle(style -> style.withHoverEvent(
                            new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal(newRole.identifier().toString()))));

            if (oldRole != null) {
                final MutableComponent oldRoleText = Harpymodloader.getRoleName(oldRole).withColor(oldRole.color())
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.literal(oldRole.identifier().toString()))));
                context.getSource()
                        .sendSuccess(() -> Component.translatable("commands.changerole.success.changed", oldRoleText,
                                newRoleText, targetPlayer.getDisplayName()), true);
            } else {
                context.getSource().sendSuccess(() -> Component.translatable("commands.changerole.success.assigned",
                        newRoleText, targetPlayer.getDisplayName()), true);
            }

            // 通知玩家角色已改变
            targetPlayer.displayClientMessage(Component.translatable("commands.changerole.player.notification", newRoleText), false);
        } catch (Exception e) {
            e.printStackTrace();
            context.getSource().sendFailure(Component.literal(e.getMessage()));
        }
        return 1;
    }
}