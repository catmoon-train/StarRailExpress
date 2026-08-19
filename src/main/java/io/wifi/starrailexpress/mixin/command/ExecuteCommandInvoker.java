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

package io.wifi.starrailexpress.mixin.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.content.command.misc.CommandPredicate;
import io.wifi.starrailexpress.content.vote.VoteManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandInvoker {

  @Unique
  private static ArgumentBuilder<CommandSourceStack, ?> sre$addConditional(
      CommandNode<CommandSourceStack> commandNode,
      ArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
      boolean isIf,
      CommandPredicate predicate) {
    return argumentBuilder
        .fork(commandNode, ctx -> {
          // 对应原版 expect()
          boolean result = predicate.test(ctx);
          return (result == isIf)
              ? Collections.singleton((CommandSourceStack) ctx.getSource())
              : Collections.emptyList();
        })
        .executes(ctx -> {
          if (isIf == predicate.test(ctx)) {
            ((CommandSourceStack) ctx.getSource()).sendSuccess(
                () -> Component.translatable("commands.execute.conditional.pass"), false);
            return 1;
          } else {
            throw new SimpleCommandExceptionType(
                Component.translatable("commands.execute.conditional.fail")).create();
          }
        });
  }

  @Inject(method = "addConditionals", at = @At("RETURN"), cancellable = true)
  private static void sre$addCustomConditionals(
      CommandNode<CommandSourceStack> commandNode,
      LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder,
      boolean isIf,
      CommandBuildContext buildContext,
      CallbackInfoReturnable<ArgumentBuilder<CommandSourceStack, ?>> cir) {
    literalArgumentBuilder.then(
        Commands.literal("sre:role")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional( // ← 移到这里
                        commandNode,
                        Commands.argument("role_id", RoleArgumentType.create(false)), // 最末端，无子节点
                        isIf,
                        ctx -> {
                          ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                          SRERole compare_role = RoleArgumentType.getRole(ctx, "role_id");
                          var roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
                          SRERole player_role = roleWorldComponent.getRole(player);
                          if (player_role == null)
                            return false;
                          return RoleUtils.compareRole(compare_role, player_role);
                        }))));
    literalArgumentBuilder.then(
        Commands.literal("sre:modifier")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("modifier_id", ModifierArgumentType.create()),
                        isIf,
                        ctx -> {
                          ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                          SREModifier compare_modifier = ModifierArgumentType.getModifier(ctx, "modifier_id");
                          if (compare_modifier == null)
                            return false;
                          var worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
                          return worldModifierComponent.isModifier(player, compare_modifier);
                        }))));
    literalArgumentBuilder.then(
        Commands.literal("sre:participate")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("is_join", BoolArgumentType.bool()),
                        isIf,
                        ctx -> {
                          ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                          boolean judgeJoin = BoolArgumentType.getBool(ctx, "is_join");
                          var cca = ParticipationComponent.KEY.getNullable(player.level());
                          if (cca == null)
                            return false;
                          return judgeJoin == cca.isParticipating(player.getUUID());
                        }))));
    literalArgumentBuilder.then(
        Commands.literal("sre:gamemode")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("gamemode", GameModeArgumentType.gameMode()),
                isIf,
                ctx -> {
                  GameMode gameMode = GameModeArgumentType.getGameModeArgument(ctx, "gamemode");
                  SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(ctx.getSource().getLevel());
                  return gameWorldComponent.getGameMode().identifier.equals(gameMode.identifier);
                })));
    literalArgumentBuilder.then(
        Commands.literal("sre:role_type")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("role_type", IntegerArgumentType.integer(-1, 5)),
                        isIf,
                        ctx -> {
                          ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                          int role_type = IntegerArgumentType.getInteger(ctx, "role_type");
                          var roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
                          SRERole player_role = roleWorldComponent.getRole(player);
                          int player_role_type = PlayerRoleWeightManager.getRoleType(player_role);
                          return player_role_type == role_type;
                        }))));
    // ── 新增：sre:vote_status 条件 ──────────────────────────
    literalArgumentBuilder.then(
        Commands.literal("sre:vote_status")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("status", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                      builder.suggest("idle");
                      builder.suggest("active");
                      builder.suggest("paused");
                      return builder.buildFuture();
                    }),
                isIf,
                ctx -> {
                  String desired = StringArgumentType.getString(ctx, "status");
                  return VoteManager.isStatus(desired);
                })));

    cir.setReturnValue(literalArgumentBuilder);
  }
}
