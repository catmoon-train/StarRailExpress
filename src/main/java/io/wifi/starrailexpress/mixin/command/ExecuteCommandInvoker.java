package io.wifi.starrailexpress.mixin.command;

import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.utils.RoleUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.command.misc.CommandPredicate;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerPlayer;

@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandInvoker {

  @Shadow
  private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addConditional(
      CommandNode<CommandSourceStack> node,
      T builder,
      boolean isIf,
      @Coerce CommandPredicate predicate) {
    throw new AssertionError();
  }

  @Inject(method = "addConditionals", at = @At("RETURN"), cancellable = true)
  private static void mymod$addCustomConditionals(
      CommandNode<CommandSourceStack> commandNode,
      LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder,
      boolean isIf,
      CommandBuildContext buildContext,
      CallbackInfoReturnable<ArgumentBuilder<CommandSourceStack, ?>> cir) {
    literalArgumentBuilder.then(
        Commands.literal("sre:role")
            .then(addConditional(
                commandNode,
                Commands.argument("target_player", EntityArgument.player())
                    .then(Commands.argument("role_id", RoleArgumentType.create(false))),
                isIf,
                ctx -> {
                  ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                  SRERole compare_role = RoleArgumentType.getRole(ctx, "role_id");
                  var roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
                  SRERole player_role = roleWorldComponent.getRole(player);
                  if (player_role == null) {
                    return (isIf ? false : true);
                  }
                  if (RoleUtils.compareRole(compare_role, player_role)) {
                    return (isIf ? true : false);
                  }
                  return (isIf ? false : true);
                })));
    literalArgumentBuilder.then(
        Commands.literal("sre:modifier")
            .then(addConditional(
                commandNode,
                Commands.argument("target_player", EntityArgument.player())
                    .then(Commands.argument("modifier_id", ModifierArgumentType.create())),
                isIf,
                ctx -> {
                  ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                  SREModifier compare_modifier = ModifierArgumentType.getModifier(ctx, "modifier_id");
                  var worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
                  if (compare_modifier == null) {
                    return (isIf ? false : true);
                  }
                  if (worldModifierComponent.isModifier(player, compare_modifier)) {
                    return (isIf ? true : false);
                  }
                  return (isIf ? false : true);
                })));
    literalArgumentBuilder.then(
        Commands.literal("sre:role_type")
            .then(addConditional(
                commandNode,
                Commands.argument("target_player", EntityArgument.player())
                    .then(Commands.argument("role_type", IntegerArgumentType.integer(0, 5))),
                isIf,
                ctx -> {
                  ServerPlayer player = EntityArgument.getPlayer(ctx, "target_player");
                  int role_type = IntegerArgumentType.getInteger(ctx, "role_type");

                  var roleWorldComponent = SRERoleWorldComponent.KEY.get(player.level());
                  SRERole player_role = roleWorldComponent.getRole(player);
                  int player_role_type = PlayerRoleWeightManager.getRoleType(player_role);
                  if (player_role_type == role_type) {
                    return (isIf ? true : false);
                  }
                  return (isIf ? false : true);
                })));
    cir.setReturnValue(literalArgumentBuilder);
  }
}
