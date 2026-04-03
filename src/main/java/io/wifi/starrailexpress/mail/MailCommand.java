package io.wifi.starrailexpress.mail;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 管理员邮件指令。
 * 
 * <pre>
 * /sre:mail send &lt;targets&gt; &lt;title&gt; &lt;content&gt;                       -- 发送纯文本邮件
 * /sre:mail send &lt;targets&gt; &lt;title&gt; &lt;content&gt; item &lt;item&gt; [count]  -- 携带物品附件
 * /sre:mail send &lt;targets&gt; &lt;title&gt; &lt;content&gt; command &lt;cmd&gt;        -- 携带领取指令
 * /sre:mail clear &lt;targets&gt;                                         -- 清空邮箱
 * /sre:mail open &lt;targets&gt;                                          -- 强制打开邮箱界面
 * </pre>
 */
public class MailCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
      CommandBuildContext registryAccess) {
    dispatcher.register(
        Commands.literal("sre:mail")
            .requires(source -> source.hasPermission(2))
            // /sre:mail send <targets> <title> <content>
            .then(Commands.literal("send")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("title", StringArgumentType.string())
                        .then(Commands.argument("content", StringArgumentType.greedyString())
                            .executes(ctx -> executeSendText(
                                ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets"),
                                StringArgumentType.getString(ctx, "title"),
                                StringArgumentType.getString(ctx, "content")))))))

            // /sre:mail senditem <targets> <title> <content> <item> [count]
            .then(Commands.literal("senditem")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("title", StringArgumentType.string())
                        .then(Commands.argument("content", StringArgumentType.string())
                            .then(Commands.argument("item", ItemArgument.item(registryAccess))
                                .executes(ctx -> executeSendItem(
                                    ctx.getSource(),
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "title"),
                                    StringArgumentType.getString(ctx, "content"),
                                    ItemArgument.getItem(ctx, "item"),
                                    1))
                                .then(Commands.argument("count",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
                                    .executes(ctx -> executeSendItem(
                                        ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        StringArgumentType.getString(ctx, "title"),
                                        StringArgumentType.getString(ctx, "content"),
                                        ItemArgument.getItem(ctx, "item"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,
                                            "count")))))))))

            // /sre:mail sendcmd <targets> <title> <content> <command>
            .then(Commands.literal("sendcmd")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("title", StringArgumentType.string())
                        .then(Commands.argument("content", StringArgumentType.string())
                            .then(Commands.argument("command", StringArgumentType.greedyString())
                                .executes(ctx -> executeSendCommand(
                                    ctx.getSource(),
                                    EntityArgument.getPlayers(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "title"),
                                    StringArgumentType.getString(ctx, "content"),
                                    StringArgumentType.getString(ctx, "command"))))))))

            // /sre:mail clear <targets>
            .then(Commands.literal("clear")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> executeClear(
                        ctx.getSource(),
                        EntityArgument.getPlayers(ctx, "targets")))))

            // /sre:mail open <targets>
            .then(Commands.literal("open")
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(ctx -> executeOpen(
                        ctx.getSource(),
                        EntityArgument.getPlayers(ctx, "targets"))))));
  }

  private static int executeSendText(CommandSourceStack source,
      Collection<ServerPlayer> targets,
      String title, String content) {
    String senderName = source.getTextName();
    for (ServerPlayer player : targets) {
      Mail mail = new Mail(UUID.randomUUID(), senderName, title, content,
          List.of(), List.of(), System.currentTimeMillis(), 0);
      MailboxComponent.KEY.get(player).sendMail(mail);
    }
    source.sendSuccess(() -> Component.literal(
        "§a已向 " + targets.size() + " 名玩家发送邮件: " + title), true);
    return targets.size();
  }

  private static int executeSendItem(CommandSourceStack source,
      Collection<ServerPlayer> targets,
      String title, String content,
      ItemInput itemInput, int count) throws CommandSyntaxException {
    String senderName = source.getTextName();
    for (ServerPlayer player : targets) {
      ItemStack stack = itemInput.createItemStack(count, false);
      List<ItemStack> attachments = new ArrayList<>();
      attachments.add(stack);
      Mail mail = new Mail(UUID.randomUUID(), senderName, title, content,
          attachments, List.of(), System.currentTimeMillis(), 0);
      MailboxComponent.KEY.get(player).sendMail(mail);
    }
    source.sendSuccess(() -> Component.literal(
        "§a已向 " + targets.size() + " 名玩家发送带附件邮件: " + title), true);
    return targets.size();
  }

  private static int executeSendCommand(CommandSourceStack source,
      Collection<ServerPlayer> targets,
      String title, String content,
      String command) {
    String senderName = source.getTextName();
    for (ServerPlayer player : targets) {
      List<String> commands = new ArrayList<>();
      commands.add(command);
      Mail mail = new Mail(UUID.randomUUID(), senderName, title, content,
          List.of(), commands, System.currentTimeMillis(), 0);
      MailboxComponent.KEY.get(player).sendMail(mail);
    }
    source.sendSuccess(() -> Component.literal(
        "§a已向 " + targets.size() + " 名玩家发送带指令邮件: " + title), true);
    return targets.size();
  }

  private static int executeClear(CommandSourceStack source,
      Collection<ServerPlayer> targets) {
    for (ServerPlayer player : targets) {
      MailboxComponent mailbox = MailboxComponent.KEY.get(player);
      mailbox.clearAllMails();
      mailbox.sync();
    }
    source.sendSuccess(() -> Component.literal(
        "§a已清空 " + targets.size() + " 名玩家的邮箱"), true);
    return targets.size();
  }

  private static int executeOpen(CommandSourceStack source,
      Collection<ServerPlayer> targets) {
    for (ServerPlayer player : targets) {
      MailboxComponent mailbox = MailboxComponent.KEY.get(player);
      mailbox.sync();
      io.wifi.starrailexpress.network.NetworkHandler.sendToClientPlayer(
          OpenMailboxScreenPayload.INSTANCE, player);
    }
    source.sendSuccess(() -> Component.literal(
        "§a已为 " + targets.size() + " 名玩家打开邮箱"), true);
    return targets.size();
  }
}
