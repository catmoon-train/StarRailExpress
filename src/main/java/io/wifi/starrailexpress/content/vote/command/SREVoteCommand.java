package io.wifi.starrailexpress.content.vote.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Either;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import io.wifi.starrailexpress.content.vote.VoteSession.VoteResultOption;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public class SREVoteCommand {

  private static final List<VoteOption> pendingOptions = new ArrayList<>();
  private static Component pendingTitle = Component.literal("Vote");
  public static ResourceLocation DATA_STORAGE_ID = SRE.id("vote_results");
  public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (commandContext,
      suggestionsBuilder) -> {
    ServerFunctionManager functionManager = commandContext.getSource().getServer().getFunctions();
    SharedSuggestionProvider.suggestResource(functionManager.getTagNames(), suggestionsBuilder, "#");
    return SharedSuggestionProvider.suggestResource(functionManager.getFunctionNames(), suggestionsBuilder);
  };

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
    var root = Commands.literal("sre:vote")
        .requires(src -> src.hasPermission(2));

    var titleNode = Commands.literal("title")
        .then(Commands.argument("text", ComponentArgument.textComponent(registryAccess))
            .executes(ctx -> {
              pendingTitle = ComponentArgument.getComponent(ctx, "text");
              ctx.getSource().sendSuccess(() -> Component.translatable("vote.title.set", pendingTitle), true);
              return 1;
            }));

    var addNode = Commands.literal("add")
        .then(Commands.literal("player")
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> addPlayerOption(ctx, EntityArgument.getPlayer(ctx, "target"), null, null))
                .then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("option_desciption", ComponentArgument.textComponent(registryAccess))
                        .executes(ctx -> addPlayerOption(ctx, EntityArgument.getPlayer(ctx, "target"),
                            StringArgumentType.getString(ctx, "id"),
                            ComponentArgument.getComponent(ctx, "option_desciption")))))))
        .then(Commands.literal("text")
            .then(Commands.argument("text", ComponentArgument.textComponent(registryAccess))
                .executes(ctx -> addTextOption(ctx, ComponentArgument.getComponent(ctx, "text"), null, null))
                .then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("option_desciption", ComponentArgument.textComponent(registryAccess))
                        .executes(ctx -> addTextOption(ctx, ComponentArgument.getComponent(ctx, "text"),
                            StringArgumentType.getString(ctx, "id"),
                            ComponentArgument.getComponent(ctx, "option_desciption")))))))
        .then(Commands.literal("item")
            .then(Commands.argument("item", ItemArgument.item(registryAccess))
                .executes(ctx -> addItemOption(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, true), null))
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes(ctx -> addItemOption(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, true),
                        StringArgumentType.getString(ctx, "id"))))));

    var listNode = Commands.literal("list")
        .executes(ctx -> {
          if (pendingOptions.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No pending options."));
            return 0;
          }
          Component msg = Component.literal("=== Pending Vote Options ===");
          for (int i = 0; i < pendingOptions.size(); i++) {
            msg = msg.copy().append("\n[" + i + "] ").append(pendingOptions.get(i).display())
                .append(" (" + pendingOptions.get(i).resultId() + ")");
          }
          Component finalMsg = msg;
          ctx.getSource().sendSuccess(() -> finalMsg, false);
          return pendingOptions.size();
        });

    var removeNode = Commands.literal("remove")
        .then(Commands.argument("index", IntegerArgumentType.integer(0))
            .executes(SREVoteCommand::removeOption));

    // 增强的 start 命令，支持 multiSelect
    var startNode = Commands.literal("start")
        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
            .executes(ctx -> startVote(ctx,
                IntegerArgumentType.getInteger(ctx, "duration"),
                false, false, 10, null, 1))
            .then(Commands.argument("allowReVote", BoolArgumentType.bool())
                .executes(ctx -> startVote(ctx,
                    IntegerArgumentType.getInteger(ctx, "duration"),
                    BoolArgumentType.getBool(ctx, "allowReVote"),
                    false, 10, null, 1))
                .then(Commands.argument("showResults", BoolArgumentType.bool())
                    .executes(ctx -> startVote(ctx,
                        IntegerArgumentType.getInteger(ctx, "duration"),
                        BoolArgumentType.getBool(ctx, "allowReVote"),
                        BoolArgumentType.getBool(ctx, "showResults"),
                        10, null, 1))
                    .then(Commands.argument("syncInterval", IntegerArgumentType.integer(0))
                        .executes(ctx -> startVote(ctx,
                            IntegerArgumentType.getInteger(ctx, "duration"),
                            BoolArgumentType.getBool(ctx, "allowReVote"),
                            BoolArgumentType.getBool(ctx, "showResults"),
                            IntegerArgumentType.getInteger(ctx, "syncInterval"),
                            null, 1))
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> startVote(ctx,
                                IntegerArgumentType.getInteger(ctx, "duration"),
                                BoolArgumentType.getBool(ctx, "allowReVote"),
                                BoolArgumentType.getBool(ctx, "showResults"),
                                IntegerArgumentType.getInteger(ctx, "syncInterval"),
                                EntityArgument.getPlayers(ctx, "targets"),
                                1))
                            .then(Commands.argument("multiSelect", IntegerArgumentType.integer(1))
                                .executes(ctx -> startVote(ctx,
                                    IntegerArgumentType.getInteger(ctx, "duration"),
                                    BoolArgumentType.getBool(ctx, "allowReVote"),
                                    BoolArgumentType.getBool(ctx, "showResults"),
                                    IntegerArgumentType.getInteger(ctx, "syncInterval"),
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    IntegerArgumentType.getInteger(ctx, "multiSelect")))
                                .then(Commands.argument("function", FunctionArgument.functions())
                                    .suggests(SUGGEST_FUNCTION)
                                    .executes(ctx -> startVote(ctx,
                                        IntegerArgumentType.getInteger(ctx, "duration"),
                                        BoolArgumentType.getBool(ctx, "allowReVote"),
                                        BoolArgumentType.getBool(ctx, "showResults"),
                                        IntegerArgumentType.getInteger(ctx, "syncInterval"),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        IntegerArgumentType.getInteger(ctx, "multiSelect"),
                                        FunctionArgument.getFunctionOrTag(ctx, "function"))))))))));

    var stopNode = Commands.literal("stop").executes(ctx -> {
      VoteManager.stopCurrentVote();
      ctx.getSource().sendSuccess(() -> Component.literal("Vote stopped.").withStyle(ChatFormatting.RED), true);
      return 1;
    });
    var pauseNode = Commands.literal("pause").executes(ctx -> {
      VoteManager.pauseCurrentVote();
      ctx.getSource().sendSuccess(() -> Component.literal("Vote paused.").withStyle(ChatFormatting.RED), true);
      return 1;
    });
    var resumeNode = Commands.literal("resume").executes(ctx -> {
      VoteManager.resumeCurrentVote();
      ctx.getSource().sendSuccess(() -> Component.literal("Vote resumed.").withStyle(ChatFormatting.RED), true);
      return 1;
    });
    var clearNode = Commands.literal("clear").executes(ctx -> {
      VoteManager.clear();
      pendingOptions.clear();
      pendingTitle = Component.literal("Vote");
      ctx.getSource().sendSuccess(() -> Component.literal("Vote cleared."), true);
      ctx.getSource().getServer().getCommandStorage().set(DATA_STORAGE_ID, new CompoundTag());
      return 1;
    });
    var statusNode = Commands.literal("status").executes(ctx -> {
      var session = VoteManager.getCurrentSession();
      if (session == null) {
        ctx.getSource().sendFailure(Component.literal("No active vote."));
        return 0;
      }
      long remaining = session.isEnded() ? 0
          : session.isPaused() ? -1 : Math.max(0, (session.getEndTick() - VoteManager.getCurrentTick()) / 20);
      Component msg = Component.literal("=== Vote Status ===")
          .append("\nState: ").append(VoteManager.getStatusString())
          .append("\nTitle: ").append(session.getTitle())
          .append("\nOptions: " + session.getOptions().size())
          .append("\nTotal votes: " + session.getTotalVotes())
          .append("\nMax select: " + session.getMaxSelectCount());
      if (session.getTargetPlayers() != null)
        msg = msg.copy().append("\nTarget players: " + session.getTargetPlayers().size());
      else
        msg = msg.copy().append("\nTarget players: All");
      msg = msg.copy().append("\nRemaining: ").append(session.isPaused() ? "Paused" : remaining + "s");
      Component finalMsg = msg;
      ctx.getSource().sendSuccess(() -> finalMsg, false);
      return 1;
    });
    var resultNode = Commands.literal("result").executes(ctx -> {
      var session = VoteManager.getCurrentSession();
      if (session == null) {
        ctx.getSource().sendFailure(Component.literal("No vote data."));
        return 0;
      }
      Component msg = Component.literal("=== Vote Results ===")
          .append("\nTitle: ").append(session.getTitle())
          .append("\nTotal votes: " + session.getTotalVotes());

      var results = session.getResults();
      if (results.isEmpty()) {
        msg = msg.copy().append("\nNo votes cast yet.");
      } else {
        for (var entry : results.entrySet()) {
          msg = msg.copy().append("\n  [Option ").append(entry.getValue().option.display())
              .append("]: " + entry.getValue().count() + " votes");
        }
      }
      Component finalMsg = msg;
      ctx.getSource().sendSuccess(() -> finalMsg, false);
      return 1;
    });

    dispatcher.register(root
        .then(titleNode)
        .then(addNode)
        .then(listNode)
        .then(startNode)
        .then(removeNode)
        .then(stopNode)
        .then(pauseNode)
        .then(resumeNode)
        .then(clearNode)
        .then(statusNode)
        .then(resultNode));
  }

  // 辅助方法同原版，但 startVote 签名调整
  private static int startVote(CommandContext<CommandSourceStack> ctx, int dur, boolean allow, boolean show,
      int interval, @Nullable Collection<ServerPlayer> targets, int multiSelect) {
    return startVote(ctx, dur, allow, show, interval, targets, multiSelect, null);
  }

  private static CompoundTag getVoteOptionCompoundTag(VoteResultOption optresult, CommandSourceStack source) {
    var ttag = new CompoundTag();
    var opt = optresult.option();
    ttag.putInt("id", optresult.id());
    ttag.putInt("count", optresult.count());
    ttag.putString("rid", opt.resultId());
    ttag.putString("display", Component.Serializer.toJson(opt.display(), source.registryAccess()));
    ttag.putString("type", opt.typeId().toString());
    if (opt.isItem() && opt instanceof VoteOption.ItemOption ito) {
      ttag.put("item", ito.stack().save(source.registryAccess()));
    } else if (opt.isPlayer() && opt instanceof VoteOption.PlayerOption ito) {
      var player_info_tag = new CompoundTag();
      player_info_tag.putUUID("id",
          ito.uuid());
      player_info_tag.putString("display_name",
          ito.display().getString());
      ttag.put("player", player_info_tag);
    }
    return ttag;
  }

  private static int startVote(CommandContext<CommandSourceStack> ctx, int dur, boolean allow, boolean show,
      int interval, @Nullable Collection<ServerPlayer> targets, int multiSelect,
      com.mojang.datafixers.util.Pair<ResourceLocation, Either<CommandFunction<CommandSourceStack>, Collection<CommandFunction<CommandSourceStack>>>> func) {
    var source = ctx.getSource();
    if (pendingOptions.size() < 2) {
      source.sendFailure(Component.literal("Need at least 2 options."));
      return 0;
    }
    var builder = VoteManager.builder(pendingTitle)
        .duration(dur * 20)
        .allowReVote(allow)
        .showResults(show)
        .syncInterval(interval * 20)
        .maxSelect(multiSelect);
    for (VoteOption opt : pendingOptions)
      builder.addOption(opt);
    if (targets != null && !targets.isEmpty())
      builder.targetPlayers(targets);
    builder.callback(session -> {
      var tag = new CompoundTag();
      var tag_results = new CompoundTag();
      var tag_top_results = new CompoundTag();
      {
        // 存储所有results
        for (var entry : session.getResults().entrySet()) {
          tag_results.put(entry.getKey(), getVoteOptionCompoundTag(entry.getValue(), source));
        }
      }
      {
        // 存储获胜者

        // 存储所有results
        var tag_top_result_entries = new ListTag();
        for (var entry : session.getTopResults()) {
          tag_top_result_entries.add(getVoteOptionCompoundTag(entry.getValue(), source));
        }
        tag_top_results.put("entries", tag_top_result_entries);
      }
      {
        tag.put("results", tag_results);
        tag.put("tops", tag_top_results);
      }
      source.getServer().getCommandStorage().set(DATA_STORAGE_ID, tag);
      source.sendSuccess(
          () -> Component.translatable("Vote data have been saved to DataStorage[%s]", DATA_STORAGE_ID.toString())
              .withStyle(ChatFormatting.GREEN),
          false);
      if (func != null)
        GameUtils.executeFunction(source, func.getFirst().toString());
    });
    var session = builder.start();
    if (session != null) {
      source.sendSuccess(() -> Component.literal("Vote started!"), true);
    } else {
      source.sendFailure(Component.literal("Another vote is already active."));
    }
    return 1;
  }

  // addPlayerOption, addTextOption, addItemOption, removeOption 方法保持不变
  private static int addPlayerOption(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String voteId,
      Component desc) {
    if (voteId != null && !voteId.isEmpty() && !voteId.isBlank()) {
      pendingOptions.add(VoteOption.player(player, voteId, desc));
    } else {
      pendingOptions.add(VoteOption.player(player, desc));
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("vote.added.player", player.getDisplayName()), true);
    return 1;
  }

  private static int addTextOption(CommandContext<CommandSourceStack> ctx, Component text, String voteId,
      Component desc) {
    if (voteId != null && !voteId.isEmpty() && !voteId.isBlank()) {
      pendingOptions.add(VoteOption.text(text, voteId, desc));
    } else {
      pendingOptions.add(VoteOption.text(text, desc));
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("vote.added.text", text), true);
    return 1;
  }

  private static int addItemOption(CommandContext<CommandSourceStack> ctx, ItemStack stack, String voteId) {
    if (voteId != null && !voteId.isEmpty() && !voteId.isBlank()) {
      pendingOptions.add(VoteOption.item(stack, voteId));
    } else {
      pendingOptions.add(VoteOption.item(stack));
    }
    ctx.getSource().sendSuccess(() -> Component.translatable("vote.added.item", stack.getHoverName()), true);
    return 1;
  }

  private static int removeOption(CommandContext<CommandSourceStack> ctx) {
    int idx = IntegerArgumentType.getInteger(ctx, "index");
    if (idx < pendingOptions.size()) {
      pendingOptions.remove(idx);
      ctx.getSource().sendSuccess(() -> Component.translatable("vote.removed", idx), true);
    } else {
      ctx.getSource().sendFailure(Component.literal("Index out of bounds"));
    }
    return 1;
  }
}