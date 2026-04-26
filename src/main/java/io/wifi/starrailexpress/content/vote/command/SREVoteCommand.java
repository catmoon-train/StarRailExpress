package io.wifi.starrailexpress.content.vote.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.vote.VoteManager;
import io.wifi.starrailexpress.content.vote.VoteOption;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.Nullable;

public class SREVoteCommand {

  private static final List<VoteOption> pendingOptions = new ArrayList<>();
  private static Component pendingTitle = Component.literal("Vote");

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
    var root = Commands.literal("sre:vote")
        .requires(src -> src.hasPermission(2));

    // ── title <text> ─────────────────────────────────
    var titleNode = Commands.literal("title")
        .then(Commands.argument("text", ComponentArgument.textComponent(registryAccess))
            .executes(ctx -> {
              pendingTitle = ComponentArgument.getComponent(ctx, "text");
              ctx.getSource().sendSuccess(
                  () -> Component.translatable("vote.title.set", pendingTitle), true);
              return 1;
            }));

    // ── add <player|text|item> ────────────────────────
    var addNode = Commands.literal("add")
        .then(Commands.literal("player")
            .then(Commands.argument("target", EntityArgument.player())
                .executes(ctx -> addPlayerOption(ctx, EntityArgument.getPlayer(ctx, "target")))))
        .then(Commands.literal("text")
            .then(Commands.argument("text", ComponentArgument.textComponent(registryAccess))
                .executes(ctx -> addTextOption(ctx, ComponentArgument.getComponent(ctx, "text")))))
        .then(Commands.literal("item")
            .then(Commands.argument("item", ItemArgument.item(registryAccess))
                .executes(ctx -> addItemOption(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, true)))));

    // ── list ─────────────────────────────────────────
    var listNode = Commands.literal("list")
        .executes(ctx -> {
          if (pendingOptions.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No pending options. Use /sre:vote add ..."));
            return 0;
          }
          Component msg = Component.literal("=== Pending Vote Options ===");
          for (int i = 0; i < pendingOptions.size(); i++) {
            VoteOption opt = pendingOptions.get(i);
            Component display = opt.display();
            msg = msg.copy().append("\n[" + i + "] ").append(display);
          }
          Component finalMsg = msg;
          ctx.getSource().sendSuccess(() -> finalMsg, false);
          return pendingOptions.size();
        });

    // ── remove <index> ────────────────────────────────
    var removeNode = Commands.literal("remove")
        .then(Commands.argument("index", IntegerArgumentType.integer(0))
            .executes(SREVoteCommand::removeOption));

    // ── start <duration> [allowReVote] [showResults] [syncInterval] [targets] ──
    var startNode = Commands.literal("start")
        .then(Commands.argument("duration", IntegerArgumentType.integer(1))
            .executes(ctx -> startVote(ctx,
                IntegerArgumentType.getInteger(ctx, "duration"),
                false, false, 10, null))
            .then(Commands.argument("allowReVote", BoolArgumentType.bool())
                .executes(ctx -> startVote(ctx,
                    IntegerArgumentType.getInteger(ctx, "duration"),
                    BoolArgumentType.getBool(ctx, "allowReVote"),
                    false, 10, null))
                .then(Commands.argument("showResults", BoolArgumentType.bool())
                    .executes(ctx -> startVote(ctx,
                        IntegerArgumentType.getInteger(ctx, "duration"),
                        BoolArgumentType.getBool(ctx, "allowReVote"),
                        BoolArgumentType.getBool(ctx, "showResults"),
                        10, null))
                    .then(Commands.argument("syncInterval", IntegerArgumentType.integer(0))
                        .executes(ctx -> startVote(ctx,
                            IntegerArgumentType.getInteger(ctx, "duration"),
                            BoolArgumentType.getBool(ctx, "allowReVote"),
                            BoolArgumentType.getBool(ctx, "showResults"),
                            IntegerArgumentType.getInteger(ctx, "syncInterval"),
                            null))
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> startVote(ctx,
                                IntegerArgumentType.getInteger(ctx, "duration"),
                                BoolArgumentType.getBool(ctx, "allowReVote"),
                                BoolArgumentType.getBool(ctx, "showResults"),
                                IntegerArgumentType.getInteger(ctx, "syncInterval"),
                                EntityArgument.getPlayers(ctx, "targets"))))))));

    // ── stop / pause / resume / clear ──────────────
    var stopNode = Commands.literal("stop")
        .executes(ctx -> {
          VoteManager.stopCurrentVote();
          ctx.getSource().sendSuccess(() -> Component.literal("[SRE-VOTE] Stopped vote!").withStyle(ChatFormatting.RED),
              true);
          return 1;
        });
    var pauseNode = Commands.literal("pause")
        .executes(ctx -> {

          ctx.getSource().sendSuccess(() -> Component.literal("[SRE-VOTE] Paused vote!").withStyle(ChatFormatting.RED),
              true);
          VoteManager.pauseCurrentVote();
          return 1;
        });
    var resumeNode = Commands.literal("resume")
        .executes(ctx -> {
          ctx.getSource().sendSuccess(() -> Component.literal("[SRE-VOTE] Resumed vote!").withStyle(ChatFormatting.RED),
              true);
          VoteManager.resumeCurrentVote();
          return 1;
        });
    var clearNode = Commands.literal("clear")
        .executes(ctx -> {
          VoteManager.clear();
          pendingOptions.clear();
          pendingTitle = Component.literal("Vote");
          ctx.getSource().sendSuccess(() -> Component.literal("Vote data cleared."), true);
          return 1;
        });

    // ── status ─────────────────────────────────────
    var statusNode = Commands.literal("status")
        .executes(ctx -> {
          var session = VoteManager.getCurrentSession();
          if (session == null) {
            ctx.getSource().sendFailure(Component.literal("No vote data."));
            return 0;
          }
          long remaining = session.isEnded() ? 0
              : session.isPaused() ? -1 : Math.max(0, (session.getEndTick() - VoteManager.getCurrentTick()) / 20);

          Component msg = Component.literal("=== Vote Status ===");
          msg = msg.copy().append("\nState: ").append(VoteManager.getStatusString());
          msg = msg.copy().append("\nTitle: ").append(session.getTitle());
          msg = msg.copy().append("\nOptions: " + session.getOptions().size());
          msg = msg.copy().append("\nTotal votes: " + session.getTotalVotes());
          if (session.getTargetPlayers() != null) {
            msg = msg.copy().append("\nTarget players: " + session.getTargetPlayers().size());
          } else {
            msg = msg.copy().append("\nTarget players: All");
          }

          if (session.isEnded()) {
            msg = msg.copy().append("\n(Ended)");
          } else if (session.isPaused()) {
            msg = msg.copy().append("\nRemaining: Paused");
          } else {
            msg = msg.copy().append("\nRemaining: " + remaining + "s");
          }

          msg = msg.copy().append("\nShow results: " + session.isShowResults());
          msg = msg.copy().append("\nAllow re-vote: " + session.isAllowReVote());

          if (session.isShowResults()) {
            var results = session.getResults();
            for (var entry : results.entrySet()) {
              msg = msg.copy().append("\n  [Option " + entry.getKey() + "]: " + entry.getValue() + " votes");
            }
          }
          Component finalMsg = msg;
          ctx.getSource().sendSuccess(() -> finalMsg, false);
          return 1;
        });

    // ── result ─────────────────────────────────────
    var resultNode = Commands.literal("result")
        .executes(ctx -> {
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
              msg = msg.copy().append("\n  [Option " + entry.getKey() + "]: " + entry.getValue() + " votes");
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

  // ═════════════════════════════════════════════════════
  // 辅助方法
  // ═════════════════════════════════════════════════════

  private static int addPlayerOption(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
    pendingOptions.add(VoteOption.player(player));
    ctx.getSource().sendSuccess(() -> Component.translatable("vote.added.player", player.getDisplayName()), true);
    return 1;
  }

  private static int addTextOption(CommandContext<CommandSourceStack> ctx, Component text) {
    pendingOptions.add(VoteOption.text(text));
    ctx.getSource().sendSuccess(() -> Component.translatable("vote.added.text", text), true);
    return 1;
  }

  private static int addItemOption(CommandContext<CommandSourceStack> ctx, ItemStack stack) {
    pendingOptions.add(VoteOption.item(stack));
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

  /**
   * 启动投票（带可选目标玩家）。
   * 
   * @param targets 限定玩家集合，null 或空表示全体玩家
   */
  private static int startVote(CommandContext<CommandSourceStack> ctx,
      int durationSeconds,
      boolean allowReVote,
      boolean showResults,
      int syncIntervalSec,
      @Nullable Collection<ServerPlayer> targets) {
    var source = ctx.getSource();
    if (pendingOptions.size() < 2) {
      source.sendFailure(Component.literal("Need at least 2 options. Use /sre:vote add ..."));
      return 0;
    }

    var builder = VoteManager.builder(pendingTitle)
        .duration(durationSeconds * 20)
        .allowReVote(allowReVote)
        .showResults(showResults)
        .syncInterval(syncIntervalSec * 20);
    for (VoteOption opt : pendingOptions)
      builder.addOption(opt);

    if (targets != null && !targets.isEmpty()) {
      builder.targetPlayers(targets);
    }
    builder.callback((session) -> {
      var tag = new CompoundTag();
      var tag_results = new ListTag();
      var tag_options = new ListTag();
      var tag_top_results = new ListTag();
      {
        // 存储options
        int idx = 0;
        for (var opt : session.getOptions()) {
          var ttag = new CompoundTag();
          ttag.putInt("id", idx);
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
          tag_options.add(ttag);
          idx++;
        }
      }
      {
        // 存储所有results
        for (var entry : session.getResults().entrySet()) {
          var ttag = new CompoundTag();
          ttag.putInt(entry.getKey(), entry.getValue());
          tag_options.add(ttag);
        }
      }
      {
        // 存储获胜者

        // 存储所有results
        for (var entry : session.getTopResults()) {
          var ttag = new CompoundTag();
          ttag.putInt(entry.getKey(), entry.getValue());
          tag_options.add(ttag);
        }
      }
      {
        tag.put("results", tag_results);
        tag.put("tops", tag_top_results);
        tag.put("options", tag_options);
      }
      var storageID = SRE.id("vote_results");
      source.getServer().getCommandStorage().set(storageID, tag);
      source.sendSystemMessage(Component.translatable("Vote data have been saved to DataStorage[%s]", storageID));
      session.getResults();
    });
    var session = builder.start();
    if (session != null) {
      source.sendSuccess(() -> Component.literal("Vote started!"), true);
      // 不清空选项，不清空标题，方便复用
    } else {
      source.sendFailure(Component.literal("Another vote is already active"));
    }
    return 1;
  }
}