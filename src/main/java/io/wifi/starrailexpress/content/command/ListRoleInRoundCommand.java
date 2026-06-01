package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

public class ListRoleInRoundCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("listGameRoles").requires(source -> source.hasPermission(2))
                .executes(ListRoleInRoundCommand::execute));
    }

    public static Component generateRoleInRoundText(ServerLevel level) {
        var gameWorldComponent = SREGameWorldComponent.KEY.get(level);
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(level);
        boolean first = true;
        var texts = Component.literal("");
        for (ServerPlayer player : level.players()) {
            var role = gameWorldComponent.getRole(player);
            var name = RoleUtils.getRoleOrModifierNameWithColor(role);
            var modifierTexts = Component.literal("");
            var modifiers = worldModifierComponent.getModifiers(player);
            if (!modifiers.isEmpty()) {
                modifierTexts = (ComponentUtils.formatList(modifiers,
                        modifier -> Component.translatable("[%s]", modifier.getName(false))
                                .withColor(modifier.color)))
                        .copy();
            }
            texts = texts.append(
                    Component.translatable((first ? "" : "\n") + "%s: %s%s",
                            player.getName().copy().withStyle(ChatFormatting.WHITE), name, modifierTexts)
                            .withStyle(ChatFormatting.GRAY));
            first = false;

        }
        return texts;
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var level = source.getLevel();
        if (level == null)
            level = source.getServer().overworld();
        final var resultTexts = Component.literal("")
                .append(Component.literal("Roles:\n").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(generateRoleInRoundText(level));
        source.sendSuccess(() -> resultTexts, false);
        return 1;
    }
}