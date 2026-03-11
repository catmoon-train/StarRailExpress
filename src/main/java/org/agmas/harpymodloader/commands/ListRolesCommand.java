package org.agmas.harpymodloader.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.harpymodloader.modifiers.HMLModifiers;

public class ListRolesCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("listRoles").executes((ListRolesCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        if(!Harpymodloader.isMojangVerify) {
            return 1;
        }
        HarpyModLoaderConfig.HANDLER.save();
        final MutableComponent message = Component.empty();
        message.append(Component.translatable("commands.listroles.role.title")).append("\n");
        message.append(ComponentUtils.formatList(TMMRoles.ROLES.values(), Component.literal("\n"), role -> {
            final boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabled.contains(role.identifier().toString());
            final MutableComponent status = createStatus(context.getSource(), disabled, "/setEnabledRole " + role.identifier() + " " + disabled);
            return buildElementText(Harpymodloader.getRoleName(role).withColor(role.color()), role.identifier(), status);
        }));
        message.append("\n\n");
        message.append(Component.translatable("commands.listroles.modifier.title")).append("\n");
        message.append(ComponentUtils.formatList(HMLModifiers.MODIFIERS, Component.literal("\n"), modifier -> {
            final boolean disabled = HarpyModLoaderConfig.HANDLER.instance().disabledModifiers.contains(modifier.identifier().toString());
            final MutableComponent status = createStatus(context.getSource(), disabled, "/setEnabledModifier " + modifier.identifier() + " " + disabled);
            return buildElementText(modifier.getName().withColor(modifier.color), modifier.identifier(), status);
        }));

        context.getSource().sendSystemMessage(message);
        return 1;
    }

    private static MutableComponent buildElementText(Component name, ResourceLocation identifier, Component status) {
        return Component.empty().append(name.copy()).append(" ").append(Component.literal("(" + identifier + ")")).append(" ").append(status);
    }

    private static MutableComponent createStatus(CommandSourceStack source, boolean disabled, String cmd) {
        String key = disabled ? "disabled" : "enabled";
        return Component.translatable("commands.listroles.status." + key + ".text").withStyle(style -> {
            if (source.hasPermission(2)) {
                return style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.listroles.status." + key + ".hover", cmd))).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
            } else {
                return style;
            }
        });
    }
}
