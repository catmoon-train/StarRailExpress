package io.wifi.starrailexpress.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.wifi.starrailexpress.network.OpenRoleUnlockScreenPayload;
import io.wifi.starrailexpress.unlock.RoleUnlockManager;
import io.wifi.starrailexpress.unlock.RoleUnlockStorage;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;

import org.agmas.harpymodloader.Harpymodloader;

/**
 * /sre:unlock_roles 指令
 * <ul>
 * <li>{@code /sre:unlock_roles} — 为自己打开职业解锁进度界面</li>
 * <li>{@code /sre:unlock_roles <player>} — 为指定玩家打开界面</li>
 * <li>{@code /sre:unlock_roles all} — 一键解锁所有职业（需 OP 权限）</li>
 * </ul>
 */
public class UnlockAllRolesCommand {

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("sre:unlock_roles")
        .requires((t) -> Harpymodloader.isMojangVerify) // 支持正版
        .requires(d -> d.hasPermission(2))
        // 无参数：为自己打开解锁进度界面
        .executes(ctx -> openScreen(ctx.getSource(),
            ctx.getSource().getPlayerOrException()))
        // 指定玩家：为他打开界面（需 OP）
        .then(Commands.argument("player", GameProfileArgument.gameProfile())
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> {
              ServerPlayer target = getSinglePlayer(ctx.getSource(),
                  GameProfileArgument.getGameProfiles(ctx, "player"));
              return openScreen(ctx.getSource(), target);
            }))
        // all：一键解锁所有职业（需 OP）
        .then(Commands.literal("all")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> unlockAll(ctx.getSource()))));
  }

  // ─── 子命令实现 ───────────────────────────────────────────────────────────

  private static int openScreen(CommandSourceStack source, ServerPlayer player) {
    RoleUnlockStorage storage = RoleUnlockStorage.getInstance();
    OpenRoleUnlockScreenPayload payload = new OpenRoleUnlockScreenPayload(
        storage.getGlobalGamesPlayed(),
        new ArrayList<>(storage.getForceUnlockedRoles()));
    ServerPlayNetworking.send(player, payload);
    return 1;
  }

  private static int unlockAll(CommandSourceStack source) {
    RoleUnlockManager.getInstance().unlockAll();
    int total = RoleUnlockManager.UNLOCK_THRESHOLDS.size();
    source.sendSuccess(
        () -> Component.literal("[SRE] 已强制解锁全部 " + total + " 个职业！"), true);
    return 1;
  }

  // ─── 工具方法 ─────────────────────────────────────────────────────────────

  private static ServerPlayer getSinglePlayer(CommandSourceStack source,
      Collection<GameProfile> profiles) throws CommandSyntaxException {
    if (profiles.size() != 1) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
          .dispatcherUnknownArgument().create();
    }
    GameProfile profile = profiles.iterator().next();
    ServerPlayer player = source.getServer().getPlayerList().getPlayer(profile.getId());
    if (player == null) {
      throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
          .dispatcherUnknownArgument().create();
    }
    return player;
  }
}
