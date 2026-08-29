/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.text.Collator;
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

        Collator collator = Collator.getInstance();
        players.sort((pa, pb) -> {
            boolean alive = GameUtils.isPlayerAliveAndSurvival(pa);
            boolean blive = GameUtils.isPlayerAliveAndSurvival(pb);
            if (alive && !blive) {
                return 1;
            } else if (blive && !alive) {
                return -1;
            }
            var ra = rolecca.getRole(pa.getUUID());
            var rb = rolecca.getRole(pb.getUUID());
            int rta = RoleUtils.getRoleType(ra, 6);
            int rtb = RoleUtils.getRoleType(rb, 6);
            if (rta == rtb) {
                return collator.compare(pa.getScoreboardName(), pb.getScoreboardName());
            }
            return -Integer.compare(rta, rtb);
        });
        var pt = ParticipationComponent.KEY.get(level);
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            var name = RoleUtils.getRoleOrModifierNameWithColor(role);
            var modifierTexts = Component.literal("");
            var modifiers = worldModifierComponent.getModifiers(player);
            if (!modifiers.isEmpty()) {
                modifierTexts = (ComponentUtils.formatList(modifiers,
                        modifier -> Component.translatable("[%s]", modifier.getName(false))
                                .withStyle(style -> style.withHoverEvent(
                                        new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                Component.literal(modifier.identifier().toString()))))
                                .withColor(modifier.color)))
                        .copy();
            }
            texts = texts.append(
                    Component
                            .translatable((first ? "" : "\n") + "%s %s: %s%s",
                                    (getDeathStatus(player, pt, gameWorldComponent)),
                                    player.getName().copy().withStyle(ChatFormatting.WHITE), name, modifierTexts)
                            .withStyle(ChatFormatting.GRAY));
            first = false;

        }
        return texts;
    }

    private static Component getDeathStatus(ServerPlayer player, ParticipationComponent pt,
            SREGameWorldComponent game) {
        boolean alive = GameUtils.isPlayerAliveAndSurvival(player);
        boolean nullRole = game == null ? false : !game.hasRole(player);
        boolean part = (pt != null) ? pt.isParticipating(player) : true;
        return alive ? Component.literal("[Alive]").withStyle(ChatFormatting.GREEN)
                : (part ? (nullRole ? Component.literal("[New]").withStyle(ChatFormatting.AQUA)
                        : Component.literal("[Dead]").withStyle(ChatFormatting.RED))
                        : Component.literal("[Spec]").withStyle(ChatFormatting.LIGHT_PURPLE));
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