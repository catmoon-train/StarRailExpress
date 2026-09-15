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
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.GameStatus;
import io.wifi.starrailexpress.content.command.argument.GameModeArgumentType;
import io.wifi.starrailexpress.content.command.misc.CommandPredicate;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.disguise.DisguiseQuery;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.Registries;
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
                          ServerPlayer player = EntityArgument.getPlayer(ctx,
                              "target_player");
                          SRERole compare_role = RoleArgumentType.getRole(ctx, "role_id");
                          var roleWorldComponent = SRERoleWorldComponent.KEY
                              .get(player.level());
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
                          ServerPlayer player = EntityArgument.getPlayer(ctx,
                              "target_player");
                          SREModifier compare_modifier = ModifierArgumentType.getModifier(ctx,
                              "modifier_id");
                          if (compare_modifier == null)
                            return false;
                          var worldModifierComponent = WorldModifierComponent.KEY
                              .get(player.level());
                          return worldModifierComponent.isModifier(player, compare_modifier);
                        }))));

    literalArgumentBuilder.then(
        Commands.literal("permission")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("permission_level",
                            IntegerArgumentType.integer(0, 4)),
                        isIf,
                        ctx -> {
                          ServerPlayer player = EntityArgument.getPlayer(ctx,
                              "target_player");
                          int permission = IntegerArgumentType.getInteger(ctx,
                              "permission_level");
                          return player.hasPermissions(permission);
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
                          ServerPlayer player = EntityArgument.getPlayer(ctx,
                              "target_player");
                          boolean judgeJoin = BoolArgumentType.getBool(ctx, "is_join");
                          var cca = ParticipationComponent.KEY.getNullable(player.level());
                          if (cca == null)
                            return false;
                          return judgeJoin == cca.isParticipating(player.getUUID());
                        }))));
    literalArgumentBuilder.then(
        Commands.literal("sre:game_status")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("status", StringArgumentType.string()).suggests((a, ctx) -> {
                  String inp = ctx.getRemainingLowerCase();
                  for (var status : GameStatus.values()) {
                    if (status.name().startsWith(inp)) {
                      ctx.suggest(status.name());
                    }
                  }
                  return ctx.buildFuture();
                }),
                isIf,
                ctx -> {
                  String status = StringArgumentType.getString(ctx, "status");
                  var gamecca = SREGameWorldComponent.KEY.get(ctx.getSource().getLevel());
                  GameStatus trueStatus = null;
                  for (var s : GameStatus.values()) {
                    if (s.name().toLowerCase().equals(status)) {
                      trueStatus = s;
                    }
                  }
                  if (trueStatus == null)
                    return false;
                  return trueStatus.equals(gamecca.getGameStatus());
                })));
    literalArgumentBuilder.then(
        Commands.literal("sre:area")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("id", StringArgumentType.string()),
                isIf,
                ctx -> {
                  String areaId = StringArgumentType.getString(ctx, "id");
                  var area = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
                  if (area.mapName != null && area.mapName.equals(areaId))
                    return true;
                  return false;
                })));
    literalArgumentBuilder.then(
        Commands.literal("sre:gamemode")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("gamemode", GameModeArgumentType.gameMode()),
                isIf,
                ctx -> {
                  GameMode gameMode = GameModeArgumentType.getGameModeArgument(ctx, "gamemode");
                  SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY
                      .get(ctx.getSource().getLevel());
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
                          ServerPlayer player = EntityArgument.getPlayer(ctx,
                              "target_player");
                          int role_type = IntegerArgumentType.getInteger(ctx, "role_type");
                          var roleWorldComponent = SRERoleWorldComponent.KEY
                              .get(player.level());
                          SRERole player_role = roleWorldComponent.getRole(player);
                          int player_role_type = PlayerRoleWeightManager
                              .getRoleType(player_role);
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

    // ── 新增：伪装相关条件，两个分支 ──────────────────────────
    // 1) 是否处于伪装（任意来源：实体伪装 / 职业形态 / 皮肤变形）
    literalArgumentBuilder.then(
        Commands.literal("sre:disguised")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("target_player", EntityArgument.player()),
                isIf,
                ctx -> DisguiseQuery.isDisguised(EntityArgument.getPlayer(ctx, "target_player")))));
    // 2) 是否伪装成指定实体类型（只认实体伪装这个来源）
    literalArgumentBuilder.then(
        Commands.literal("sre:disguised_type")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        // 实体类型用原版注册表参数（/summon 那个），候选与报错都跟原版一致。
                        Commands.argument("entity_type",
                            ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
                            .suggests(SuggestionProviders.SUMMONABLE_ENTITIES),
                        isIf,
                        ctx -> DisguiseQuery.isDisguisedAs(
                            EntityArgument.getPlayer(ctx, "target_player"),
                            ResourceArgument.getEntityType(ctx, "entity_type").value())))));
    // 注：外观 NBT 的判断并入原版 `if data sre:disguise <player> <path>`（见 DataCommandsMixin），
    // 不再单开一个复合匹配的分支。

    // ── 变形（MorphApi）条件，三层：任意 / 指定玩家 / 指定贴图 ──────────────
    // 注：sre:disguised 已经把变形算作「一种伪装」，这三个是**只看变形**的收窄判定。
    literalArgumentBuilder.then(
        Commands.literal("sre:morphed")
            .then(sre$addConditional(
                commandNode,
                Commands.argument("target_player", EntityArgument.player()),
                isIf,
                ctx -> DisguiseQuery.isMorphed(EntityArgument.getPlayer(ctx, "target_player")))));
    literalArgumentBuilder.then(
        Commands.literal("sre:morphed_player")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("morph_target", EntityArgument.player()),
                        isIf,
                        ctx -> DisguiseQuery.isMorphedAsPlayer(
                            EntityArgument.getPlayer(ctx, "target_player"),
                            EntityArgument.getPlayer(ctx, "morph_target"))))));
    literalArgumentBuilder.then(
        Commands.literal("sre:morphed_texture")
            .then(
                Commands.argument("target_player", EntityArgument.player())
                    .then(sre$addConditional(
                        commandNode,
                        Commands.argument("texture", ResourceLocationArgument.id()),
                        isIf,
                        ctx -> DisguiseQuery.isMorphedAsTexture(
                            EntityArgument.getPlayer(ctx, "target_player"),
                            ResourceLocationArgument.getId(ctx, "texture"))))));

    cir.setReturnValue(literalArgumentBuilder);
  }
}
