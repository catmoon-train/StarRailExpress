package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.role.RepairForcedRoleState;
import net.exmo.sre.repair.role.RepairRoleDatabase;
import net.exmo.sre.repair.role.RepairRoleDefinition;

public final class RepairRoleCommand {
    private static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = (context, builder) -> {
        for (RepairRoleDefinition role : RepairRoleDefinition.values()) {
            builder.suggest(role.id);
        }
        return builder.buildFuture();
    };

    private RepairRoleCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("cy:repairrole")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("force")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("roleId", StringArgumentType.word())
                                                .suggests(ROLE_SUGGESTIONS)
                                                .executes(context -> {
                                                    RepairRoleDefinition role = parseRole(
                                                            StringArgumentType.getString(context, "roleId"));
                                                    int changed = 0;
                                                    for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
                                                        RepairRoleDatabase.loadInto(player);
                                                        var component = ModComponents.REPAIR_ROLES.get(player);
                                                        RepairForcedRoleState.force(player.getUUID(), role.id);
                                                        component.forcedRole = role.id;
                                                        component.setSelectedRole(role);
                                                        component.sync();
                                                        changed++;
                                                        player.displayClientMessage(Component.translatable(
                                                                "message.noellesroles.repair.force_role_self",
                                                                role.displayName()).withStyle(ChatFormatting.GOLD), false);
                                                    }
                                                    int finalChanged = changed;
                                                    context.getSource().sendSuccess(() -> Component.translatable(
                                                            "message.noellesroles.repair.force_role_done",
                                                            finalChanged, role.displayName()), true);
                                                    return changed;
                                                }))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .executes(context -> {
                                            int changed = 0;
                                            for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
                                                RepairForcedRoleState.clear(player.getUUID());
                                                var component = ModComponents.REPAIR_ROLES.get(player);
                                                component.forcedRole = "";
                                                component.sync();
                                                changed++;
                                            }
                                            int finalChanged = changed;
                                            context.getSource().sendSuccess(() -> Component.translatable(
                                                    "message.noellesroles.repair.force_role_clear", finalChanged), true);
                                            return changed;
                                        })))
                        .then(Commands.literal("unlock")
                                .then(Commands.argument("players", EntityArgument.players())
                                        .then(Commands.argument("roleId", StringArgumentType.word())
                                                .suggests(ROLE_SUGGESTIONS)
                                                .executes(context -> {
                                                    RepairRoleDefinition role = parseRole(
                                                            StringArgumentType.getString(context, "roleId"));
                                                    int changed = 0;
                                                    for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
                                                        RepairRoleDatabase.loadInto(player);
                                                        ModComponents.REPAIR_ROLES.get(player).unlock(role);
                                                        changed++;
                                                    }
                                                    int finalChanged = changed;
                                                    context.getSource().sendSuccess(() -> Component.translatable(
                                                            "message.noellesroles.repair.unlock_role_done",
                                                            finalChanged, role.displayName()), true);
                                                    return changed;
                                                }))))));
    }

    private static RepairRoleDefinition parseRole(String roleId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return RepairRoleDefinition.byId(roleId).orElseThrow(() ->
                new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                        Component.translatable("message.noellesroles.repair.role_unknown", roleId)).create());
    }
}
