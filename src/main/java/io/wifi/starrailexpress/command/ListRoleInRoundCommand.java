package io.wifi.starrailexpress.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.GameWorldComponent;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;

public class ListRoleInRoundCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("listGameRoles").requires(source -> source.hasPermission(2))
                .executes(ListRoleInRoundCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var level = source.getLevel();
        if (level == null)
            level = source.getServer().overworld();
        var gameWorldComponent = GameWorldComponent.KEY.get(level);
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(level);
        var texts = Component.literal("")
                .append(Component.literal("Roles:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer player : level.players()) {
            var role = gameWorldComponent.getRole(player);
            var name = RoleUtils.getRoleOrModifierNameWithColor(role);
            var modifierTexts = Component.literal("");
            var modifiers = worldModifierComponent.getModifiers(player);
            if (!modifiers.isEmpty()) {
                modifierTexts = Component.translatable("announcement.star.modifier")
                        .withStyle(ChatFormatting.GRAY)
                        .append(ComponentUtils.formatList(modifiers,
                                modifier -> Component.translatable("[%s]", modifier.getName(false))
                                        .withColor(modifier.color)));
            }
            texts = texts.append(
                    Component.translatable("\n%s: %s%s",
                            player.getDisplayName().copy().withStyle(ChatFormatting.WHITE), name, modifierTexts)
                            .withStyle(ChatFormatting.GRAY));
        }
        final var resultTexts = texts;
        source.sendSuccess(() -> resultTexts, false);
        return 1;
    }
}