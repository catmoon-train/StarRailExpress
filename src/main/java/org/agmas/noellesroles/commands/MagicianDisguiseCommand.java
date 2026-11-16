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

package org.agmas.noellesroles.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.magician.MagicianDisguiseHelper;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.concurrent.CompletableFuture;

/**
 * 魔术师伪装测试指令。
 *
 * <p>{@code /sre:magician disguise <role>}：把自己在本局的伪装杀手身份改成指定职业，
 * 立即生效（杀手视角的身份、{@code MagicianHud}、商店都会跟着刷新）。</p>
 *
 * <p>补全 / 校验都走 {@link MagicianDisguiseHelper#isDisguisableBase(SRERole)}，
 * 因此只会列出真正可伪装的杀手职业；测试指令允许与本局已有杀手重复，
 * 所以不做「场上已有职业」去重。</p>
 */
public final class MagicianDisguiseCommand {

    private MagicianDisguiseCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:magician")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("disguise")
                                .then(Commands.argument("role", ResourceLocationArgument.id())
                                        .suggests(MagicianDisguiseCommand::suggestRoles)
                                        .executes(MagicianDisguiseCommand::setDisguise)))));
    }

    private static CompletableFuture<Suggestions> suggestRoles(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                MagicianDisguiseHelper.collectDisguisableRoles().stream()
                        .map(role -> role.identifier().toString())
                        .toList(),
                builder);
    }

    private static int setDisguise(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!RoleUtils.isPlayerTheJob(player, ModRoles.MAGICIAN)) {
            source.sendFailure(Component.translatable("command.noellesroles.magician.not_magician"));
            return 0;
        }
        ResourceLocation id = ResourceLocationArgument.getId(context, "role");
        SRERole role = TMMRoles.ROLES.get(id);
        if (role == null || !MagicianDisguiseHelper.isDisguisableBase(role)) {
            Component name = role == null
                    ? Component.literal(String.valueOf(id))
                    : roleName(role);
            source.sendFailure(Component.translatable("command.noellesroles.magician.invalid_role", name));
            return 0;
        }
        MagicianRoleData data = RoleData.getNullable(MagicianRoleData.class, player);
        if (data == null) {
            source.sendFailure(Component.translatable("command.noellesroles.magician.no_data"));
            return 0;
        }
        // setDisguiseRoleId 内部会 sync，客户端 HUD / 杀手视角身份随之刷新；商店缓存按伪装职业 ID 失效。
        data.setDisguiseRoleId(role.identifier());
        Component name = roleName(role);
        player.displayClientMessage(Component.translatable("message.magician.you_are_playing_as")
                .append(name).withStyle(ChatFormatting.GOLD), true);
        source.sendSuccess(() -> Component.translatable("command.noellesroles.magician.disguise_set", name), true);
        return 1;
    }

    private static Component roleName(SRERole role) {
        return Component.translatable("announcement.star.role." + role.identifier().getPath());
    }
}
