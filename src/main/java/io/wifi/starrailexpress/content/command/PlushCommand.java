package io.wifi.starrailexpress.content.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.api.PlushApi;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.agmas.noellesroles.init.SREFumoBlocks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 皮肤玩偶发放指令（OP，权限等级 2）。
 * <ul>
 * <li>{@code /sre:plush get <skin> [target]} —— 把 {@code <skin>} 对应的 plush
 * 发给目标玩家（缺省为执行者本人）。</li>
 * <li>{@code /sre:plush list} —— 列出所有可用的皮肤名（即当前已注册的 plush）。</li>
 * </ul>
 * 映射逻辑见 {@link PlushApi}：皮肤名 {@code X} → 物品 {@code noellesroles:X_plush}。
 * 不新建任何模型/贴图，仅复用仓库中现成的 plush。
 */
public final class PlushCommand {
  private PlushCommand() {
  }

  /** {@code <skin>} 参数的补全：建议当前所有可用的皮肤名。 */
  private static final SuggestionProvider<CommandSourceStack> SKIN_SUGGESTIONS = (ctx,
      builder) -> SharedSuggestionProvider.suggest(PlushApi.availableSkinNames(), builder);

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

    dispatcher.register(Commands.literal("sre:plush")
        .requires(source -> source.hasPermission(2))
        .then(Commands.literal("list")
            .executes(PlushCommand::listPlush))
        .then(Commands.literal("get")
            .then(Commands.argument("skin", StringArgumentType.word())
                .suggests(SKIN_SUGGESTIONS)
                .executes(ctx -> giveSelf(ctx, StringArgumentType.getString(ctx, "skin")))))
        .then(Commands.literal("give")
            .then(Commands.argument("target", EntityArgument.players())
                .then(Commands.argument("skin", StringArgumentType.word())
                    .suggests(SKIN_SUGGESTIONS)
                    .executes(ctx -> giveTargets(ctx, StringArgumentType.getString(ctx, "skin")))
                    .then(Commands.literal("profile")
                        .then(Commands.argument("profile", GameProfileArgument.gameProfile())
                            .executes((ctx) -> givePlayerCustomPlush(ctx,
                                GameProfileArgument.getGameProfiles(ctx, "profile"), null,
                                null, "target"))
                            .then(Commands.argument("click_sound", ResourceLocationArgument.id())
                                .executes((ctx) -> givePlayerCustomPlush(ctx,
                                    GameProfileArgument.getGameProfiles(ctx, "profile"), null,
                                    ResourceLocationArgument.getId(ctx, "click_sound"), "target")))))
                    .then(Commands.literal("texture")
                        .then(Commands.argument("texture", ResourceLocationArgument.id())
                            .executes((ctx) -> givePlayerCustomPlush(ctx,
                                null, ResourceLocationArgument.getId(ctx, "texture"),
                                null, "target"))
                            .then(Commands.argument("click_sound", ResourceLocationArgument.id())
                                .executes((ctx) -> givePlayerCustomPlush(ctx,
                                    null, ResourceLocationArgument.getId(ctx, "texture"),
                                    ResourceLocationArgument.getId(ctx, "click_sound"), "target"))))))))
        .then(Commands.literal("player")
            .then(Commands.literal("self")
                .executes(ctx -> givePlayerSelfPlush(ctx, null))
                .then(Commands.argument("target", EntityArgument.players())
                    .executes(ctx -> givePlayerSelfPlush(ctx, "target"))))));
  }

  private static int givePlayerCustomPlush(CommandContext<CommandSourceStack> ctx,
      Collection<GameProfile> gameProfiles, ResourceLocation texture, ResourceLocation sound, String targetArg)
      throws CommandSyntaxException {
    GameProfile gameProfile = null;
    if (gameProfiles != null) {
      if (gameProfiles.size() > 1) {
        throw ConfigCommand.createSimpleSyntaxException("More than one player.");
      }
      gameProfile = gameProfiles.stream().findFirst().orElse(null);
      if (gameProfile == null) {
        throw ConfigCommand.createSimpleSyntaxException("Require at least 1 player.");
      }
    }

    Collection<ServerPlayer> targets;
    if (targetArg == null) {
      targets = List.of(ctx.getSource().getPlayerOrException());
    } else {
      targets = EntityArgument.getPlayers(ctx, targetArg);
    }

    Item plushItem = SREFumoBlocks.CUSTOM_PLAYER_PLUSH.asItem();
    ItemStack stack = new ItemStack(plushItem);
    if (gameProfile != null)
      stack.set(DataComponents.PROFILE, new ResolvableProfile(gameProfile));
    if (sound != null)
      stack.set(DataComponents.NOTE_BLOCK_SOUND, sound);
    if (texture != null)
      stack.set(SREDataComponentTypes.TEXTURE, texture);
    for (ServerPlayer target : targets) {

      if (!target.addItem(stack) && !stack.isEmpty()) {
        target.drop(stack, false);
      }
    }
    if (targets.size() == 1) {
      ServerPlayer only = targets.iterator().next();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.player_given_custom", stack.getDisplayName(), only.getDisplayName()), true);
    } else {
      int count = targets.size();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.player_given_custom_multiple", stack.getDisplayName(), count), true);
    }
    return targets.size();

  }

  /** 发放绑定指定玩家名的自定义玩家 plush（按该玩家的皮肤渲染）。 */
  private static int givePlayerSelfPlush(CommandContext<CommandSourceStack> ctx,
      String targetArg) throws CommandSyntaxException {
    Collection<ServerPlayer> targets;
    if (targetArg == null) {
      targets = List.of(ctx.getSource().getPlayerOrException());
    } else {
      targets = EntityArgument.getPlayers(ctx, targetArg);

    }

    Item plushItem = SREFumoBlocks.CUSTOM_PLAYER_PLUSH.asItem();
    for (ServerPlayer target : targets) {
      ItemStack stack = new ItemStack(plushItem);
      stack.set(DataComponents.PROFILE, new ResolvableProfile(target.getGameProfile()));
      if (!target.addItem(stack) && !stack.isEmpty()) {
        target.drop(stack, false);
      }
    }
    if (targets.size() == 1) {
      ServerPlayer only = targets.iterator().next();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.player_given", only.getDisplayName(), only.getDisplayName()), true);
    } else {
      int count = targets.size();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.player_given_multiple", targets.size(), count), true);
    }
    return targets.size();
  }

  private static int giveSelf(CommandContext<CommandSourceStack> ctx, String skin) {
    ServerPlayer self;
    try {
      self = ctx.getSource().getPlayerOrException();
    } catch (CommandSyntaxException e) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_player"));
      return 0;
    }
    return giveTo(ctx, skin, List.of(self));
  }

  private static int giveTargets(CommandContext<CommandSourceStack> ctx, String skin) throws CommandSyntaxException {
    return giveTo(ctx, skin, EntityArgument.getPlayers(ctx, "target"));
  }

  private static int giveTo(CommandContext<CommandSourceStack> ctx, String skin, Collection<ServerPlayer> targets) {
    Optional<Item> plush = PlushApi.getPlushForSkin(skin);
    if (plush.isEmpty()) {
      ctx.getSource().sendFailure(Component.translatable("commands.sre.plush.not_found", skin));
      return 0;
    }
    Item item = plush.get();
    for (ServerPlayer target : targets) {
      ItemStack stack = (item.getDefaultInstance());
      // 背包放不下时掉落到地面，避免静默丢失
      stack.set(DataComponents.PROFILE, new ResolvableProfile(target.getGameProfile()));
      if (!target.addItem(stack) && !stack.isEmpty()) {
        target.drop(stack, false);
      }
    }
    Component plushName = new ItemStack(item).getHoverName();
    if (targets.size() == 1) {
      ServerPlayer only = targets.iterator().next();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.given", plushName, only.getDisplayName()), true);
    } else {
      int count = targets.size();
      ctx.getSource().sendSuccess(() -> Component.translatable(
          "commands.sre.plush.given_multiple", plushName, count), true);
    }
    return targets.size();
  }

  private static int listPlush(CommandContext<CommandSourceStack> ctx) {
    List<String> names = PlushApi.availableSkinNames();
    ctx.getSource().sendSuccess(() -> Component.translatable(
        "commands.sre.plush.list", names.size(), String.join(", ", names)), false);
    return names.size();
  }
}
