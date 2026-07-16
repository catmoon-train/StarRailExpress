package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.harpymodloader.commands.argument.ModifierArgumentType;
import org.agmas.harpymodloader.commands.argument.RoleArgumentType;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig;
import org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class NoellesrolesConfigCommand {
  public static void register() {
    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
      dispatcher.register(Commands.literal("tmm:config")
          .requires(c -> c.hasPermission(SREConfig.instance().spawnInfoConfigRequiredPermission))
          .then(Commands.literal("spawn_info")
              .then(Commands.literal("role")
                  .then(Commands.argument("id", RoleArgumentType.create())
                      .executes((ctx) -> {
                        SRERole role = RoleArgumentType.getRole(ctx, "id");
                        if (!role.canSetSpawnInfoInConfig()) {
                          ctx.getSource()
                              .sendFailure(Component.translatable(
                                  "cmd.config.noellesroles.spawn.disabled",
                                  RoleUtils.getRoleOrModifierTypeName(role),
                                  RoleUtils.getRoleOrModifierNameWithColor(role)));
                          return 0;
                        }
                        // %s
                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                        SpawnInfo spinfo = SpawnInfoConfig.instance().roleDetails
                            .getSpawnInfo(role);
                        Component msg = Component
                            .translatable("cmd.config.noellesroles.spawn.info",
                                RoleUtils.getRoleOrModifierTypeName(role),
                                RoleUtils.getRoleOrModifierNameWithColor(role),
                                Component.literal(spinfo.minEnabledPlayer + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.maxEnabledPlayer + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.enableChance + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.maxSpawn + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component
                                    .literal(spinfo.map.stream()
                                        .collect(Collectors.joining(", ")))
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.AQUA);
                        ctx.getSource().sendSystemMessage(msg);
                        return 1;
                      })
                      .then(Commands.literal("reset")
                          .requires(source -> source.hasPermission(2)).executes(ctx -> {
                            SRERole role = RoleArgumentType.getRole(ctx, "id");
                            if (!role.canSetSpawnInfoInConfig()) {
                              ctx.getSource()
                                  .sendFailure(Component.translatable(
                                      "cmd.config.noellesroles.spawn.disabled",
                                      RoleUtils.getRoleOrModifierTypeName(role),
                                      RoleUtils.getRoleOrModifierNameWithColor(
                                          role)));
                              return 0;
                            }
                            // %s
                            // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                            SpawnInfo spinfo = SpawnInfoConfig.instance().roleDetails
                                .getSpawnInfo(role);
                            spinfo.setEnableChance(role.defaultEnableChance)
                                .setMaps(new ArrayList<>(role.defaultSpawnMaps))
                                .setMax(role.defaultMaxCount)
                                .setMaxEnabledPlayer(role.defaultEnableMaxPlayerCount)
                                .setMinEnabledPlayer(role.defaultEnableNeedPlayerCount);
                            ctx.getSource()
                                .sendSuccess(() -> Component.translatable(
                                    "cmd.config.noellesroles.spawn.reseted",
                                    RoleUtils.getRoleOrModifierTypeName(role),
                                    RoleUtils.getRoleOrModifierNameWithColor(role)),
                                    true);
                            SpawnInfoConfig.HANDLER.save();
                            return 1;
                          }))
                      .then(Commands.literal("set")
                          .requires(source -> source.hasPermission(2))
                          .then(Commands.literal("chance")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SRERole role = RoleArgumentType.getRole(ctx,
                                        "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().roleDetails
                                        .getSpawnInfo(role);
                                    spinfo.setEnableChance(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "chance", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("max_count")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SRERole role = RoleArgumentType.getRole(ctx,
                                        "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().roleDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMax(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "max_count", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("min_player")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SRERole role = RoleArgumentType.getRole(ctx,
                                        "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().roleDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMinEnabledPlayer(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "min_player", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("max_player")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SRERole role = RoleArgumentType.getRole(ctx,
                                        "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().roleDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMaxEnabledPlayer(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "max_player", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("maps")
                              .then(Commands.literal("clear")
                                  .executes(ctx -> {
                                    SRERole role = RoleArgumentType.getRole(ctx,
                                        "id");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(
                                              Component.translatable(
                                                  "cmd.config.noellesroles.spawn.disabled",
                                                  RoleUtils
                                                      .getRoleOrModifierTypeName(
                                                          role),
                                                  RoleUtils
                                                      .getRoleOrModifierNameWithColor(
                                                          role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().roleDetails
                                        .getSpawnInfo(role);
                                    spinfo.map.clear();
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "map", "[EMPTY]"),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  }))
                              .then(Commands.argument("map", StringArgumentType.string())
                                  .then(Commands.literal("add")
                                      .executes(ctx -> {
                                        SRERole role = RoleArgumentType
                                            .getRole(ctx, "id");
                                        String num = StringArgumentType
                                            .getString(ctx, "map");
                                        if (!role.canSetSpawnInfoInConfig()) {
                                          ctx.getSource()
                                              .sendFailure(
                                                  Component
                                                      .translatable(
                                                          "cmd.config.noellesroles.spawn.disabled",
                                                          RoleUtils
                                                              .getRoleOrModifierTypeName(
                                                                  role),
                                                          RoleUtils
                                                              .getRoleOrModifierNameWithColor(
                                                                  role)));
                                          return 0;
                                        }
                                        // %s
                                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                        SpawnInfo spinfo = SpawnInfoConfig
                                            .instance().roleDetails
                                            .getSpawnInfo(role);
                                        spinfo.map.add(num);
                                        ctx.getSource()
                                            .sendSuccess(
                                                () -> Component
                                                    .translatable(
                                                        "cmd.config.noellesroles.spawn.modified",
                                                        RoleUtils
                                                            .getRoleOrModifierTypeName(
                                                                role),
                                                        RoleUtils
                                                            .getRoleOrModifierNameWithColor(
                                                                role),
                                                        "map",
                                                        "+=" + num),
                                                true);
                                        SpawnInfoConfig.HANDLER.save();
                                        return 1;
                                      }))
                                  .then(Commands.literal("remove")
                                      .executes(ctx -> {
                                        SRERole role = RoleArgumentType
                                            .getRole(ctx, "id");
                                        String num = StringArgumentType
                                            .getString(ctx, "map");
                                        if (!role.canSetSpawnInfoInConfig()) {
                                          ctx.getSource()
                                              .sendFailure(
                                                  Component
                                                      .translatable(
                                                          "cmd.config.noellesroles.spawn.disabled",
                                                          RoleUtils
                                                              .getRoleOrModifierTypeName(
                                                                  role),
                                                          RoleUtils
                                                              .getRoleOrModifierNameWithColor(
                                                                  role)));
                                          return 0;
                                        }
                                        // %s
                                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                        SpawnInfo spinfo = SpawnInfoConfig
                                            .instance().roleDetails
                                            .getSpawnInfo(role);
                                        spinfo.map.remove(num);
                                        ctx.getSource()
                                            .sendSuccess(
                                                () -> Component
                                                    .translatable(
                                                        "cmd.config.noellesroles.spawn.modified",
                                                        RoleUtils
                                                            .getRoleOrModifierTypeName(
                                                                role),
                                                        RoleUtils
                                                            .getRoleOrModifierNameWithColor(
                                                                role),
                                                        "map",
                                                        "-=" + num),
                                                true);
                                        SpawnInfoConfig.HANDLER.save();
                                        return 1;
                                      }))))

      // 啊
      )))
              .then(Commands.literal("modifier")
                  .then(Commands.argument("id", ModifierArgumentType.create())
                      .executes((ctx) -> {
                        SREModifier role = ModifierArgumentType.getModifier(ctx, "id");
                        if (!role.canSetSpawnInfoInConfig()) {
                          ctx.getSource()
                              .sendFailure(Component.translatable(
                                  "cmd.config.noellesroles.spawn.disabled",
                                  RoleUtils.getRoleOrModifierTypeName(role),
                                  RoleUtils.getRoleOrModifierNameWithColor(role)));
                          return 0;
                        }
                        // %s
                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                        SpawnInfo spinfo = SpawnInfoConfig.instance().modifierDetails
                            .getSpawnInfo(role);
                        Component msg = Component
                            .translatable("cmd.config.noellesroles.spawn.info",
                                RoleUtils.getRoleOrModifierTypeName(role),
                                RoleUtils.getRoleOrModifierNameWithColor(role),
                                Component.literal(spinfo.minEnabledPlayer + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.maxEnabledPlayer + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.enableChance + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component.literal(spinfo.maxSpawn + "")
                                    .withStyle(ChatFormatting.WHITE),
                                Component
                                    .literal(spinfo.map.stream()
                                        .collect(Collectors.joining(", ")))
                                    .withStyle(ChatFormatting.YELLOW))
                            .withStyle(ChatFormatting.AQUA);
                        ctx.getSource().sendSystemMessage(msg);
                        return 1;
                      })
                      .then(Commands.literal("reset").requires(source -> source.hasPermission(2))
                          .executes(ctx -> {
                            SREModifier role = ModifierArgumentType.getModifier(ctx, "id");
                            if (!role.canSetSpawnInfoInConfig()) {
                              ctx.getSource()
                                  .sendFailure(Component.translatable(
                                      "cmd.config.noellesroles.spawn.disabled",
                                      RoleUtils.getRoleOrModifierTypeName(role),
                                      RoleUtils.getRoleOrModifierNameWithColor(
                                          role)));
                              return 0;
                            }
                            // %s
                            // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                            SpawnInfo spinfo = SpawnInfoConfig.instance().modifierDetails
                                .getSpawnInfo(role);
                            spinfo.setEnableChance(role.defaultEnableChance)
                                .setMaps(new ArrayList<>(role.defaultSpawnMaps))
                                .setMax(role.defaultMaxCount)
                                .setMaxEnabledPlayer(role.defaultMaxPlayerCount)
                                .setMinEnabledPlayer(role.defaultNeedPlayerCount);
                            ctx.getSource()
                                .sendSuccess(
                                    () -> Component.translatable(
                                        "cmd.config.noellesroles.spawn.reseted",
                                        RoleUtils.getRoleOrModifierTypeName(
                                            role),
                                        RoleUtils
                                            .getRoleOrModifierNameWithColor(
                                                role)),
                                    true);
                            SpawnInfoConfig.HANDLER.save();
                            return 1;
                          }))
                      .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                          .then(Commands.literal("chance")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SREModifier role = ModifierArgumentType
                                        .getModifier(ctx, "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().modifierDetails
                                        .getSpawnInfo(role);
                                    spinfo.setEnableChance(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "chance", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("max_count")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SREModifier role = ModifierArgumentType
                                        .getModifier(ctx, "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().modifierDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMax(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "max_count", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("min_player")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SREModifier role = ModifierArgumentType
                                        .getModifier(ctx, "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().modifierDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMinEnabledPlayer(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "min_player", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("max_player")
                              .then(Commands
                                  .argument("num", IntegerArgumentType.integer())
                                  .executes(ctx -> {
                                    SREModifier role = ModifierArgumentType
                                        .getModifier(ctx, "id");
                                    int num = IntegerArgumentType.getInteger(ctx,
                                        "num");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(Component.translatable(
                                              "cmd.config.noellesroles.spawn.disabled",
                                              RoleUtils
                                                  .getRoleOrModifierTypeName(
                                                      role),
                                              RoleUtils
                                                  .getRoleOrModifierNameWithColor(
                                                      role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().modifierDetails
                                        .getSpawnInfo(role);
                                    spinfo.setMaxEnabledPlayer(num);
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "max_player", num),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  })))
                          .then(Commands.literal("maps")
                              .then(Commands.literal("clear")
                                  .executes(ctx -> {
                                    SREModifier role = ModifierArgumentType
                                        .getModifier(ctx, "id");
                                    if (!role.canSetSpawnInfoInConfig()) {
                                      ctx.getSource()
                                          .sendFailure(
                                              Component.translatable(
                                                  "cmd.config.noellesroles.spawn.disabled",
                                                  RoleUtils
                                                      .getRoleOrModifierTypeName(
                                                          role),
                                                  RoleUtils
                                                      .getRoleOrModifierNameWithColor(
                                                          role)));
                                      return 0;
                                    }
                                    // %s
                                    // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                    SpawnInfo spinfo = SpawnInfoConfig
                                        .instance().modifierDetails
                                        .getSpawnInfo(role);
                                    spinfo.map.clear();
                                    ctx.getSource()
                                        .sendSuccess(
                                            () -> Component.translatable(
                                                "cmd.config.noellesroles.spawn.modified",
                                                RoleUtils
                                                    .getRoleOrModifierTypeName(
                                                        role),
                                                RoleUtils
                                                    .getRoleOrModifierNameWithColor(
                                                        role),
                                                "map", "[EMPTY]"),
                                            true);
                                    SpawnInfoConfig.HANDLER.save();
                                    return 1;
                                  }))
                              .then(Commands.argument("map", StringArgumentType.string())
                                  .then(Commands.literal("add")
                                      .executes(ctx -> {
                                        SREModifier role = ModifierArgumentType
                                            .getModifier(ctx, "id");
                                        String num = StringArgumentType
                                            .getString(ctx, "map");
                                        if (!role.canSetSpawnInfoInConfig()) {
                                          ctx.getSource()
                                              .sendFailure(
                                                  Component
                                                      .translatable(
                                                          "cmd.config.noellesroles.spawn.disabled",
                                                          RoleUtils
                                                              .getRoleOrModifierTypeName(
                                                                  role),
                                                          RoleUtils
                                                              .getRoleOrModifierNameWithColor(
                                                                  role)));
                                          return 0;
                                        }
                                        // %s
                                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                        SpawnInfo spinfo = SpawnInfoConfig
                                            .instance().modifierDetails
                                            .getSpawnInfo(role);
                                        spinfo.map.add(num);
                                        ctx.getSource()
                                            .sendSuccess(
                                                () -> Component
                                                    .translatable(
                                                        "cmd.config.noellesroles.spawn.modified",
                                                        RoleUtils
                                                            .getRoleOrModifierTypeName(
                                                                role),
                                                        RoleUtils
                                                            .getRoleOrModifierNameWithColor(
                                                                role),
                                                        "map",
                                                        "+=" + num),
                                                true);
                                        SpawnInfoConfig.HANDLER.save();
                                        return 1;
                                      }))
                                  .then(Commands.literal("remove")
                                      .executes(ctx -> {
                                        SREModifier role = ModifierArgumentType
                                            .getModifier(ctx, "id");
                                        String num = StringArgumentType
                                            .getString(ctx, "map");
                                        if (!role.canSetSpawnInfoInConfig()) {
                                          ctx.getSource()
                                              .sendFailure(
                                                  Component
                                                      .translatable(
                                                          "cmd.config.noellesroles.spawn.disabled",
                                                          RoleUtils
                                                              .getRoleOrModifierTypeName(
                                                                  role),
                                                          RoleUtils
                                                              .getRoleOrModifierNameWithColor(
                                                                  role)));
                                          return 0;
                                        }
                                        // %s
                                        // 生成信息（-1为禁用）：\n最小启用玩家数：%s\n最大启用玩家数：%s\n启用概率：%s/10000\n最大生成数量：%s\n启用地图：%s
                                        SpawnInfo spinfo = SpawnInfoConfig
                                            .instance().modifierDetails
                                            .getSpawnInfo(role);
                                        spinfo.map.remove(num);
                                        ctx.getSource()
                                            .sendSuccess(
                                                () -> Component
                                                    .translatable(
                                                        "cmd.config.noellesroles.spawn.modified",
                                                        RoleUtils
                                                            .getRoleOrModifierTypeName(
                                                                role),
                                                        RoleUtils
                                                            .getRoleOrModifierNameWithColor(
                                                                role),
                                                        "map",
                                                        "-=" + num),
                                                true);

                                        SpawnInfoConfig.HANDLER.save();
                                        return 1;
                                      }))))

      // 啊
      )))));

      var configCommand = Commands.literal("tmm:config")
          .requires(source -> source.hasPermission(2)) // 需要OP权限
          .then(Commands.literal("noellesroles")
              .then(Commands.literal("reload")
                  .executes(context -> {
                    SpawnInfoConfig.HANDLER.load();
                    NoellesRolesConfig.HANDLER.load();
                    context.getSource().sendSystemMessage(
                        Component.literal("NoellesRoles configuration reloaded successfully"));
                    return 1;
                  }))
              .then(Commands.literal("reset")
                  .executes(context -> {
                    // 创建默认配置实例
                    SpawnInfoConfig.HANDLER.reset();
                    NoellesRolesConfig.HANDLER.reset();
                    return 1;
                  }))
              .then(Commands.literal("accidentalKillPunishment")
                  .then(Commands.argument("value", BoolArgumentType.bool())
                      .executes(context -> {
                        boolean value = BoolArgumentType.getBool(context, "value");
                        NoellesRolesConfig config = NoellesRolesConfig.instance();
                        config.accidentalKillPunishment = value;
                        NoellesRolesConfig.HANDLER.save();

                        String statusText = value ? "Enabled" : "Disabled";
                        context.getSource().sendSystemMessage(
                            Component
                                .literal("Innocent Punishment " + statusText
                                    + " (accidentalKillPunishment = " + value + ")")
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                        return 1;
                      })))
              .then(Commands.literal("skillEchoEvent")
                  .then(Commands.argument("value", BoolArgumentType.bool())
                      .executes(context -> {
                        boolean value = BoolArgumentType.getBool(context, "value");
                        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
                        config.skillEchoEventEnabled = value;
                        NoellesRolesConfig.HANDLER.save();
                        context.getSource().sendSystemMessage(
                            Component.literal("Skill Echo Event "
                                + (value ? "Enabled" : "Disabled"))
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                        return 1;
                      })))
              .then(Commands.literal("skillEchoRandom")
                  .then(Commands.argument("value", BoolArgumentType.bool())
                      .executes(context -> {
                        boolean value = BoolArgumentType.getBool(context, "value");
                        NoellesRolesConfig config = NoellesRolesConfig.HANDLER.instance();
                        config.skillEchoRandomBroadcastEnabled = value;
                        NoellesRolesConfig.HANDLER.save();
                        context.getSource().sendSystemMessage(
                            Component.literal("Skill Echo Random Broadcast "
                                + (value ? "Enabled" : "Disabled"))
                                .withStyle(net.minecraft.ChatFormatting.GREEN));
                        return 1;
                      }))));
      dispatcher.register(configCommand);
    });
  }
}
