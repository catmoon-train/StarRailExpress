package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.ConfigCompact.network.RoleEnableInfoPacket;
import io.wifi.ConfigCompact.ui.RoleManageConfigUI;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;

public class ListRolesCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        dispatcher.register(Commands.literal("manageRolesUI").requires(source -> source.hasPermission(2))
                .executes((ListRolesCommand::executeManage)));
        dispatcher.register(Commands.literal("listRoles").executes((ListRolesCommand::execute)));
    }

    private static int executeManage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        HashMap<ResourceLocation, Boolean> roleInfos = new HashMap<>();
        HashMap<ResourceLocation, Boolean> modifierInfos = new HashMap<>();
        for (var info : TMMRoles.ROLES.keySet()) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabled.contains(info.toString())) {
                roleInfos.put(info, false);
            } else {
                roleInfos.put(info, true);
            }
        }
        for (var info : HMLModifiers.MODIFIERS) {
            if (HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(info.identifier().toString())) {
                modifierInfos.put(info.identifier(), false);
            } else {
                modifierInfos.put(info.identifier(), true);
            }
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("Try to open Role Manage UI for %s", player.getDisplayName()), true);
        ServerPlayNetworking.send(player,
                new RoleEnableInfoPacket(new RoleManageConfigUI.RoleAndModifierSyncInfo(roleInfos, modifierInfos)));
        return 1;
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        if (!Harpymodloader.isMojangVerify) {
            return 1;
        }
        final MutableComponent message = Component.empty();
        message.append(Component.translatable("commands.listroles.role.title")).append("\n");
        message.append(ComponentUtils.formatList(TMMRoles.ROLES.values(), Component.literal("\n"), role -> {
            final boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabled
                    .contains(role.identifier().toString());
            final MutableComponent status = createStatus(context.getSource(), disabled,
                    "/setEnabledRole " + role.identifier() + " " + disabled);
            return buildElementText(Harpymodloader.getRoleName(role).withColor(role.color()), role.identifier(),
                    status);
        }));
        message.append("\n\n");
        message.append(Component.translatable("commands.listroles.modifier.title")).append("\n");
        message.append(ComponentUtils.formatList(HMLModifiers.MODIFIERS, Component.literal("\n"), modifier -> {
            final boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabledModifiers
                    .contains(modifier.identifier().toString());
            final MutableComponent status = createStatus(context.getSource(), disabled,
                    "/setEnabledModifier " + modifier.identifier() + " " + disabled);
            return buildElementText(modifier.getName().withColor(modifier.color), modifier.identifier(), status);
        }));

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private static MutableComponent buildElementText(Component name, ResourceLocation identifier, Component status) {
        return Component.empty().append(name.copy()).append(" ").append(Component.literal("(" + identifier + ")"))
                .append(" ").append(status);
    }

    private static MutableComponent createStatus(CommandSourceStack source, boolean disabled, String cmd) {
        String key = disabled ? "disabled" : "enabled";
        return Component.translatable("commands.listroles.status." + key + ".text").withStyle(style -> {
            if (source.hasPermission(2)) {
                return style
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.listroles.status." + key + ".hover", cmd)))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            } else {
                return style;
            }
        });
    }
}
