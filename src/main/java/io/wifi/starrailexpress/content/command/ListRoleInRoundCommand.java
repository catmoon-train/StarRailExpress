package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

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
        var players = new ArrayList<>(level.players());
        var rolecca = SRERoleWorldComponent.KEY.get(level);
        players.sort((pa, pb) -> {
            boolean alive = GameUtils.isPlayerAliveAndSurvival(pa);
            boolean blive = GameUtils.isPlayerAliveAndSurvival(pb);
            if (alive && !blive) {
                return -1;
            } else if (blive && !alive) {
                return 1;
            }
            var ra = rolecca.getRole(pa.getUUID());
            var rb = rolecca.getRole(pb.getUUID());
            int rta = RoleUtils.getRoleType(ra);
            int rtb = RoleUtils.getRoleType(rb);
            return -Integer.compare(rta, rtb);
        });
        for (ServerPlayer player : players) {
            boolean alive = GameUtils.isPlayerAliveAndSurvival(player);
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
                    Component
                            .translatable((first ? "" : "\n") + "%s %s: %s%s",
                                    (alive ? Component.literal("[Alive]").withStyle(ChatFormatting.GREEN)
                                            : Component.literal("[Dead]").withStyle(ChatFormatting.RED)),
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