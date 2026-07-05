package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.AreasWorldComponent.PosWithOrientation;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.block_entity.SecurityMonitorBlockEntity;
import io.wifi.starrailexpress.content.command.argument.MapLoadArgumentType;
import io.wifi.starrailexpress.content.item.BindingToolItem;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.game.data.MapStatusBarType;
import io.wifi.starrailexpress.index.TMMBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MapManagerCommand {
  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("sre:monitor").requires(source -> source.hasPermission(2))
            .then(Commands.literal("search")
                .then(Commands.argument("block_pos", BlockPosArgument.blockPos())
                    .then(Commands.literal("in_reset_template").executes((ctx) -> {

                      BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "block_pos");
                      var source = ctx.getSource();
                      var level = source.getLevel();
                      var areas = AreasWorldComponent.KEY.get(level);
                      if (level.getBlockState(blockPos).is(TMMBlocks.SECURITY_MONITOR)) {
                        if (level.getBlockEntity(blockPos) instanceof SecurityMonitorBlockEntity smbe) {
                          smbe.clearCameraPositions();
                          int count = 0;
                          BlockPos trainMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
                          BlockPos trainMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
                          BoundingBox trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);
                          for (int z = trainBox.minZ(); z <= trainBox.maxZ(); z++) {
                            for (int y = trainBox.minY(); y <= trainBox.maxY(); y++) {
                              for (int x = trainBox.minX(); x <= trainBox.maxX(); x++) {
                                if (level.getBlockState(new BlockPos(x, y, z)).is(TMMBlocks.CAMERA)) {
                                  smbe.addCameraPosition(
                                      BindingToolItem.CalcRelativePosition(blockPos, new BlockPos(x, y, z)));
                                  source.sendSystemMessage(
                                      Component.translatable("- Found camera at [%s, %s, %s]", x, y, z)
                                          .withStyle(ChatFormatting.GRAY));
                                  count++;
                                }
                              }
                            }
                          }
                          smbe.setChanged();
                          final int ccount = count;
                          source.sendSuccess(
                              () -> Component.translatable("Successfully added %s cameras to security monitor %s",
                                  ccount, blockPos.toShortString()).withStyle(ChatFormatting.GREEN),
                              true);
                          return count;
                        }
                      }
                      source.sendFailure(Component.literal("Not a security monitor or invaild position!")
                          .withStyle(ChatFormatting.RED));
                      return 0;
                    }))
                    .then(Commands.argument("range", IntegerArgumentType.integer(0, 200)).executes((ctx) -> {
                      int range = IntegerArgumentType.getInteger(ctx, "range");
                      BlockPos blockPos = BlockPosArgument.getBlockPos(ctx, "block_pos");
                      var source = ctx.getSource();
                      var level = source.getLevel();
                      if (level.getBlockState(blockPos).is(TMMBlocks.SECURITY_MONITOR)) {
                        if (level.getBlockEntity(blockPos) instanceof SecurityMonitorBlockEntity smbe) {
                          smbe.clearCameraPositions();
                          int count = 0;
                          int miny = Math.max(blockPos.getY() - range, level.getMinBuildHeight());
                          int maxy = Math.min(blockPos.getY() + range, level.getMaxBuildHeight());
                          for (int x = blockPos.getX() - range; x <= blockPos.getX() + range; x++) {
                            for (int y = miny; y <= maxy; y++) {
                              for (int z = blockPos.getZ() - range; z <= blockPos.getZ() + range; z++) {
                                if (level.getBlockState(new BlockPos(x, y, z)).is(TMMBlocks.CAMERA)) {
                                  smbe.addCameraPosition(
                                      BindingToolItem.CalcRelativePosition(blockPos, new BlockPos(x, y, z)));
                                  source.sendSystemMessage(
                                      Component.translatable("- Found camera at [%s, %s, %s]", x, y, z)
                                          .withStyle(ChatFormatting.GRAY));
                                  count++;
                                }
                              }
                            }
                          }
                          smbe.setChanged();
                          final int ccount = count;
                          source.sendSuccess(
                              () -> Component.translatable("Successfully added %s cameras to security monitor %s",
                                  ccount, blockPos.toShortString()).withStyle(ChatFormatting.GREEN),
                              true);
                          return count;
                        }
                      }
                      source.sendFailure(Component.literal("Not a security monitor or invaild position!")
                          .withStyle(ChatFormatting.RED));
                      return 0;
                    })))));
    dispatcher.register(
        Commands.literal("sre:area_manager")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("create_new")
                .requires(source -> source.hasPermission(3))
                .executes((ctx) -> {
                  var areas = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
                  areas.canJump = false;
                  areas.canSwim = false;
                  areas.enableOxygenDrowning = false;
                  areas.mapStatusBar = MapStatusBarType.NONE;
                  areas.disabledTasks = new HashSet<>();
                  areas.disabledRoles = new HashSet<>();
                  areas.enableSceneTask = new HashSet<>();
                  areas.haveOutsideSound = false;
                  areas.sceneOutsideSound = "train";
                  areas.mapName = "new_area";
                  areas.mustCopy = false;
                  areas.noReset = false;
                  areas.sceneOffsetEnabled = false;
                  areas.snowEnabled = false;
                  areas.sandEnabled = false;
                  areas.fogEnabled = true;
                  areas.fogEnd = 200.0f;
                  areas.fogShape = "SPHERE";
                  areas.sceneOffsetX = 0;
                  areas.sceneOffsetY = 0;
                  areas.sceneOffsetZ = 0;
                  io.wifi.starrailexpress.scenery.server.SceneLibrary.clearScene(areas);
                  areas.weather = "clear";
                  areas.gravity = 0.08;
                  areas.effect = new java.util.ArrayList<>();
                  areas.time = 18000;
                  areas.daylightCycle = false;
                  areas.weatherCycle = false;
                  areas.minigameQuestEnabled = false;
                  areas.sync();
                  ctx.getSource().sendSuccess(
                      () -> Component.literal("Created new area configuration")
                          .withStyle(style -> style.withColor(0x00FF00)),
                      true);
                  return 1;
                }))
            .then(Commands.literal("set")
                .then(setSpawnPos())
                .then(setSpectatorSpawnPos())
                .then(setReadyArea())
                .then(setPlayArea())
                .then(setSceneArea())
                .then(setResetTemplateArea())
                .then(setResetPasteArea())
                .then(setPlayAreaOffset())
                .then(setRoomCount())
                .then(setRoomPositions())
                .then(setCanJump())
                .then(setCanSwim())
                .then(setEnableOxygenDrowning())
                .then(setMapStatusBar())
                .then(setNoReset())
                .then(setHaveOutsideSound())
                .then(setSceneOutsideSound())
                .then(setSceneOffsetEnabled())
                .then(setSnowEnabled())
                .then(setSandEnabled())
                .then(setFogEnabled())
                .then(setFogEnd())
                .then(setFogShape())
                .then(setSceneOffsetX())
                .then(setSceneOffsetY())
                .then(setSceneOffsetZ())
                .then(setMustCopy())
                .then(setMapName())
                .then(setDisabledTasks())
                .then(setDisabledRoles())
                .then(setEnableSceneTask())
                .then(setWeather())
                .then(setGravity())
                .then(setEffect())
                .then(setTime())
                .then(setDaylightCycle())
                .then(setWeatherCycle())
                .then(setMinigameQuestEnabled())
                .then(setInitialItems()))
            .then(Commands.literal("get")
                .requires(source -> source.hasPermission(2))
                .then(getSpawnPos())
                .then(getSpectatorSpawnPos())
                .then(buildGetAABB("readyArea", AreasWorldComponent::getReadyArea))
                .then(buildGetAABB("playArea", AreasWorldComponent::getPlayArea))
                .then(buildGetAABB("sceneArea", AreasWorldComponent::getSceneArea))
                .then(buildGetAABB("resetTemplateArea", AreasWorldComponent::getResetTemplateArea))
                .then(buildGetAABB("resetPasteArea", AreasWorldComponent::getResetPasteArea))
                .then(getPlayAreaOffset())
                .then(getRoomCount())
                .then(getRoomPositions())
                .then(buildGetSimple("canJump", a -> String.valueOf(a.canJump)))
                .then(buildGetSimple("canSwim", a -> String.valueOf(a.canSwim)))
                .then(buildGetSimple("enableOxygenDrowning", a -> String.valueOf(a.enableOxygenDrowning)))
                .then(buildGetSimple("mapStatusBar", a -> String.valueOf(a.mapStatusBar == null ? MapStatusBarType.NONE : a.mapStatusBar)))
                .then(buildGetSimple("noReset", a -> String.valueOf(a.noReset)))
                .then(buildGetSimple("haveOutsideSound", a -> String.valueOf(a.haveOutsideSound)))
                .then(buildGetSimple("sceneOutsideSound", a -> a.sceneOutsideSound))
                .then(buildGetSimple("sceneOffsetEnabled", a -> String.valueOf(a.sceneOffsetEnabled)))
                .then(buildGetSimple("snowEnabled", a -> String.valueOf(a.snowEnabled)))
                .then(buildGetSimple("sandEnabled", a -> String.valueOf(a.sandEnabled)))
                .then(buildGetSimple("fogEnabled", a -> String.valueOf(a.fogEnabled)))
                .then(buildGetSimple("fogEnd", a -> String.valueOf(a.fogEnd)))
                .then(buildGetSimple("fogShape", a -> "\"" + a.fogShape + "\""))
                .then(buildGetSimple("sceneOffsetX", a -> String.valueOf(a.sceneOffsetX)))
                .then(buildGetSimple("sceneOffsetY", a -> String.valueOf(a.sceneOffsetY)))
                .then(buildGetSimple("sceneOffsetZ", a -> String.valueOf(a.sceneOffsetZ)))
                .then(buildGetSimple("mustCopy", a -> String.valueOf(a.mustCopy)))
                .then(buildGetSimple("mapName", a -> "\"" + a.mapName + "\""))
                .then(buildGetSimple("weather", a -> a.weather))
                .then(buildGetSimple("gravity", a -> String.valueOf(a.gravity)))
                .then(buildGetSimple("effect", a -> a.effect.isEmpty() ? "(none)" : String.join(", ", a.effect)))
                .then(buildGetSimple("time", a -> String.valueOf(a.time)))
                .then(buildGetSimple("daylightCycle", a -> String.valueOf(a.daylightCycle)))
                .then(buildGetSimple("weatherCycle", a -> String.valueOf(a.weatherCycle)))
                .then(buildGetSimple("minigameQuestEnabled", a -> String.valueOf(a.minigameQuestEnabled)))
                .then(buildGetSimple("initialItems", a -> a.initialItems.isEmpty() ? "(none)" : String.join(", ", a.initialItems)))
                .then(getDisabledTasks())
                .then(getDisabledRoles())
                .then(getEnableSceneTask()))
            .then(Commands.literal("remove")
                .requires(source -> source.hasPermission(3))
                .then(Commands.argument("mapName", MapLoadArgumentType.string())
                    .executes(context -> executeRemove(
                        context.getSource(),
                        StringArgumentType.getString(context, "mapName")))))
            .then(Commands.literal("save")
                .then(Commands.argument("mapName", MapLoadArgumentType.string())
                    .executes(context -> executeSave(
                        context.getSource(),
                        StringArgumentType.getString(context, "mapName"),
                        false))
                    .then(Commands.literal("force")
                        .requires(source -> source.hasPermission(3))
                        .executes(context -> executeSave(
                            context.getSource(),
                             StringArgumentType.getString(context, "mapName"),
                             true)))))
            .then(buildMapTools())
            .then(Commands.literal("info")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> executeList(ctx.getSource()))));
  }

  // ======================== 辅助方法 ========================

  private static String formatPosWithOrientation(PosWithOrientation pos) {
    if (pos == null)
      return "null";
    return String.format("[%.2f, %.2f, %.2f, %.2f, %.2f]",
        pos.pos.x, pos.pos.y, pos.pos.z, pos.yaw, pos.pitch);
  }

  private static String formatAABB(AABB box) {
    if (box == null)
      return "null";
    return String.format("[%.2f, %.2f, %.2f, %.2f, %.2f, %.2f]",
        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
  }

  private static String formatAABBMin(AABB box) {
    if (box == null)
      return "null";
    return String.format("[%.2f, %.2f, %.2f]", box.minX, box.minY, box.minZ);
  }

  private static String formatAABBMax(AABB box) {
    if (box == null)
      return "null";
    return String.format("[%.2f, %.2f, %.2f]", box.maxX, box.maxY, box.maxZ);
  }

  private static String formatVec3(Vec3 vec) {
    if (vec == null)
      return "null";
    return String.format("[%.2f, %.2f, %.2f]", vec.x, vec.y, vec.z);
  }

  private static String formatRoomPositions(Map<Integer, Vec3> map) {
    if (map == null || map.isEmpty())
      return "{}";
    return map.entrySet().stream()
        .map(e -> e.getKey() + ": " + formatVec3(e.getValue()))
        .collect(Collectors.joining(", ", "{", "}"));
  }

  private static String formatDisabledTasks(Set<String> set) {
    if (set == null || set.isEmpty())
      return "[]";
    return set.stream().collect(Collectors.joining(", ", "[", "]"));
  }

  private static void sendSetFeedback(CommandSourceStack source, String fieldName, String value) {
    source.sendSuccess(
        () -> Component.literal("修改成功：" + fieldName + "：" + value)
            .withStyle(style -> style.withColor(0x00FF00)),
        true);
  }

  private static void sendGetFeedback(CommandSourceStack source, String fieldName, String value) {
    source.sendSuccess(
        () -> Component.literal("查询：" + fieldName + " = " + value)
            .withStyle(style -> style.withColor(ChatFormatting.AQUA)),
        false);
  }

  // ======================== set 实现方法 ========================

  // 1. spawnPos
  private static void setSpawnPos(CommandSourceStack source, Vec3 pos, float yaw, float pitch) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    PosWithOrientation newPos = new PosWithOrientation(pos.x, pos.y, pos.z, yaw, pitch);
    areas.setSpawnPos(newPos);
    areas.sync();
    sendSetFeedback(source, "spawnPos", formatPosWithOrientation(newPos));
  }

  private static void setSpectatorSpawnPos(CommandSourceStack source, Vec3 pos, float yaw, float pitch) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    PosWithOrientation newPos = new PosWithOrientation(pos.x, pos.y, pos.z, yaw, pitch);
    areas.setSpectatorSpawnPos(newPos);
    areas.sync();
    sendSetFeedback(source, "spectatorSpawnPos", formatPosWithOrientation(newPos));
  }

  // 2. AABB 通用设置（基于 BlockPos）
  private static void updateAABB(AreasWorldComponent areas,
      BiConsumer<AreasWorldComponent, AABB> setter,
      AABB newBox, CommandSourceStack source, String fieldName) {
    setter.accept(areas, newBox);
    if (fieldName.startsWith("sceneArea")) {
      areas.setSceneAreaConfigured(true);
      if (areas.getSceneScroll() == AreasWorldComponent.ScrollAxis.NONE) {
        areas.setSceneScroll(AreasWorldComponent.ScrollAxis.X);
      }
      io.wifi.starrailexpress.scenery.server.SceneAssetServer.invalidate(source.getLevel(), "场景源区域已修改");
    }
    areas.sync();
    sendSetFeedback(source, fieldName, formatAABB(newBox));
  }

  private static void setAABBFull(AreasWorldComponent areas,
      BiConsumer<AreasWorldComponent, AABB> setter,
      BlockPos min, BlockPos max,
      CommandSourceStack source, String fieldName) {
    AABB box = new AABB(min.getX(), min.getY(), min.getZ(),
        max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    updateAABB(areas, setter, box, source, fieldName);
  }

  private static void setAABBMin(AreasWorldComponent areas,
      BiConsumer<AreasWorldComponent, AABB> setter,
      Function<AreasWorldComponent, AABB> getter,
      BlockPos min,
      CommandSourceStack source, String fieldName) {
    AABB old = getter.apply(areas);
    AABB newBox = new AABB(min.getX(), min.getY(), min.getZ(),
        old.maxX, old.maxY, old.maxZ);
    updateAABB(areas, setter, newBox, source, fieldName + ".min");
  }

  private static void setAABBMax(AreasWorldComponent areas,
      BiConsumer<AreasWorldComponent, AABB> setter,
      Function<AreasWorldComponent, AABB> getter,
      BlockPos max,
      CommandSourceStack source, String fieldName) {
    AABB old = getter.apply(areas);
    AABB newBox = new AABB(old.minX, old.minY, old.minZ,
        max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);
    updateAABB(areas, setter, newBox, source, fieldName + ".max");
  }

  // 3. playAreaOffset
  private static void setPlayAreaOffset(CommandSourceStack source, Vec3 offset) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.setPlayAreaOffset(offset);
    areas.sync();
    sendSetFeedback(source, "playAreaOffset", formatVec3(offset));
  }

  // 4. roomCount
  private static void setRoomCount(CommandSourceStack source, int count) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.setRoomCount(count);
    areas.sync();
    sendSetFeedback(source, "roomCount", String.valueOf(count));
  }

  // 5. roomPositions
  private static void addRoomPosition(CommandSourceStack source, int roomId, Vec3 pos) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.setRoomPosition(roomId, pos);
    areas.sync();
    sendSetFeedback(source, "roomPositions." + roomId, formatVec3(pos));
  }

  private static void removeRoomPosition(CommandSourceStack source, int roomId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.getRoomPositions().remove(roomId) != null) {
      areas.sync();
      sendSetFeedback(source, "roomPositions.remove", String.valueOf(roomId));
    } else {
      source.sendFailure(Component.literal("房间 " + roomId + " 没有定义位置"));
    }
  }

  // 6. 布尔值字段
  private static void setCanJump(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.canJump = value;
    areas.sync();
    sendSetFeedback(source, "canJump", String.valueOf(value));
  }

  private static void setCanSwim(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.canSwim = value;
    areas.sync();
    sendSetFeedback(source, "canSwim", String.valueOf(value));
  }

  private static void setEnableOxygenDrowning(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.enableOxygenDrowning = value;
    areas.sync();
    sendSetFeedback(source, "enableOxygenDrowning", String.valueOf(value));
  }

  private static void setMapStatusBar(CommandSourceStack source, MapStatusBarType value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.mapStatusBar = value == null ? MapStatusBarType.NONE : value;
    areas.sync();
    sendSetFeedback(source, "mapStatusBar", areas.mapStatusBar.name());
  }

  private static void setNoReset(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.noReset = value;
    areas.sync();
    sendSetFeedback(source, "noReset", String.valueOf(value));
  }

  private static void setHaveOutsideSound(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.haveOutsideSound = value;
    areas.sync();
    sendSetFeedback(source, "haveOutsideSound", String.valueOf(value));
  }

  private static void setSceneOffsetEnabled(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sceneOffsetEnabled = value;
    areas.sync();
    sendSetFeedback(source, "sceneOffsetEnabled", String.valueOf(value));
  }

  private static void setSnowEnabled(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.snowEnabled = value;
    areas.sync();
    sendSetFeedback(source, "snowEnabled", String.valueOf(value));
  }

  private static void setSandEnabled(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sandEnabled = value;
    areas.sync();
    sendSetFeedback(source, "sandEnabled", String.valueOf(value));
  }

  private static void setFogEnabled(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.fogEnabled = value;
    areas.sync();
    sendSetFeedback(source, "fogEnabled", String.valueOf(value));
  }

  private static void setFogEnd(CommandSourceStack source, float value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.fogEnd = value;
    areas.sync();
    sendSetFeedback(source, "fogEnd", String.valueOf(value));
  }

  private static void setFogShape(CommandSourceStack source, String value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.fogShape = value;
    areas.sync();
    sendSetFeedback(source, "fogShape", "\"" + value + "\"");
  }

  private static void setMustCopy(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.mustCopy = value;
    areas.sync();
    sendSetFeedback(source, "mustCopy", String.valueOf(value));
  }

  // 7. 双精度浮点字段
  private static void setSceneOffsetX(CommandSourceStack source, double value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sceneOffsetX = value;
    areas.sync();
    sendSetFeedback(source, "sceneOffsetX", String.valueOf(value));
  }

  private static void setSceneOffsetY(CommandSourceStack source, double value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sceneOffsetY = value;
    areas.sync();
    sendSetFeedback(source, "sceneOffsetY", String.valueOf(value));
  }

  private static void setSceneOffsetZ(CommandSourceStack source, double value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sceneOffsetZ = value;
    areas.sync();
    sendSetFeedback(source, "sceneOffsetZ", String.valueOf(value));
  }

  // 8. mapName
  private static void setMapName(CommandSourceStack source, String name) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.mapName = name;
    areas.sync();
    sendSetFeedback(source, "mapName", "\"" + name + "\"");
  }

  // 9. disabledTasks
  private static void addDisabledTask(CommandSourceStack source, String taskId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.disabledTasks == null)
      areas.disabledTasks = new HashSet<>();
    areas.disabledTasks.add(taskId);
    areas.sync();
    sendSetFeedback(source, "disabledTasks.add", taskId);
  }

  private static void removeDisabledTask(CommandSourceStack source, String taskId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.disabledTasks != null && areas.disabledTasks.remove(taskId)) {
      areas.sync();
      sendSetFeedback(source, "disabledTasks.remove", taskId);
    } else {
      source.sendFailure(Component.literal("任务 " + taskId + " 不在禁用列表中"));
    }
  }

  private static void addDisabledRole(CommandSourceStack source, String roleId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.disabledRoles == null)
      areas.disabledRoles = new HashSet<>();
    areas.disabledRoles.add(roleId);
    areas.sync();
    sendSetFeedback(source, "disabledRoles.add", roleId);
  }

  private static void removeDisabledRole(CommandSourceStack source, String roleId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.disabledRoles != null && areas.disabledRoles.remove(roleId)) {
      areas.sync();
      sendSetFeedback(source, "disabledRoles.remove", roleId);
    } else {
      source.sendFailure(Component.literal("职业 " + roleId + " 不在禁用列表中"));
    }
  }

  // 10. weather
  private static void setWeather(CommandSourceStack source, String value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.weather = value;
    areas.sync();
    sendSetFeedback(source, "weather", "\"" + value + "\"");
  }

  // 11. gravity
  private static void setGravity(CommandSourceStack source, double value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.gravity = value;
    areas.sync();
    sendSetFeedback(source, "gravity", String.valueOf(value));
  }

  // 12. effect
  private static void setEffect(CommandSourceStack source, String value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.effect = new java.util.ArrayList<>();
    if (!value.isEmpty()) {
        // 支持逗号分隔的多个效果，如 "minecraft:speed,2,minecraft:jump_boost,1"
        // 这里简单地把整个 value 当作单个效果
        areas.effect.add(value);
    }
    areas.sync();
    sendSetFeedback(source, "effect", "\"" + value + "\"");
  }

  // 13. time
  private static void setTime(CommandSourceStack source, long value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.time = value;
    areas.sync();
    sendSetFeedback(source, "time", String.valueOf(value));
  }

  // 14. daylightCycle
  private static void setDaylightCycle(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.daylightCycle = value;
    areas.sync();
    sendSetFeedback(source, "daylightCycle", String.valueOf(value));
  }

  // 15. weatherCycle
  private static void setWeatherCycle(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.weatherCycle = value;
    areas.sync();
    sendSetFeedback(source, "weatherCycle", String.valueOf(value));
  }

  // 15b. minigameQuestEnabled
  private static void setMinigameQuestEnabled(CommandSourceStack source, boolean value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.minigameQuestEnabled = value;
    areas.sync();
    sendSetFeedback(source, "minigameQuestEnabled", String.valueOf(value));
  }

  // 16. initialItems
  private static void setInitialItems(CommandSourceStack source, String value) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.initialItems = new java.util.ArrayList<>();
    if (!value.isEmpty()) {
      // 支持逗号分隔，格式如 "minecraft:diamond,2,minecraft:stick,1"
      String[] parts = value.split(",");
      for (int i = 0; i < parts.length; i += 2) {
        if (i + 1 < parts.length) {
          String itemId = parts[i].trim();
          String countStr = parts[i + 1].trim();
          areas.initialItems.add(itemId + ";" + countStr);
        }
      }
    }
    areas.sync();
    sendSetFeedback(source, "initialItems", "\"" + value + "\"");
  }

  // ======================== list 子命令实现 ========================

  private static int executeList(CommandSourceStack source) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    StringBuilder sb = new StringBuilder();
    sb.append("\n=== 当前区域配置 ===\n");
    sb.append("spawnPos: ").append(formatPosWithOrientation(areas.getSpawnPos())).append("\n");
    sb.append("spectatorSpawnPos: ").append(formatPosWithOrientation(areas.getSpectatorSpawnPos())).append("\n");
    sb.append("readyArea: ").append(formatAABB(areas.getReadyArea())).append("\n");
    sb.append("playArea: ").append(formatAABB(areas.getPlayArea())).append("\n");
    sb.append("sceneArea: ").append(formatAABB(areas.getSceneArea())).append("\n");
    sb.append("resetTemplateArea: ").append(formatAABB(areas.getResetTemplateArea())).append("\n");
    sb.append("resetPasteArea: ").append(formatAABB(areas.getResetPasteArea())).append("\n");
    sb.append("playAreaOffset: ").append(formatVec3(areas.getPlayAreaOffset())).append("\n");
    sb.append("roomCount: ").append(areas.getRoomCount()).append("\n");
    sb.append("roomPositions: ").append(formatRoomPositions(areas.getRoomPositions())).append("\n");
    sb.append("canJump: ").append(areas.canJump).append("\n");
    sb.append("canSwim: ").append(areas.canSwim).append("\n");
    sb.append("enableOxygenDrowning: ").append(areas.enableOxygenDrowning).append("\n");
    sb.append("mapStatusBar: ").append(areas.mapStatusBar == null ? MapStatusBarType.NONE : areas.mapStatusBar).append("\n");
    sb.append("noReset: ").append(areas.noReset).append("\n");
    sb.append("haveOutsideSound: ").append(areas.haveOutsideSound).append("\n");
    sb.append("sceneOutsideSound: \"").append(areas.sceneOutsideSound).append("\"\n");
    sb.append("sceneOffsetEnabled: ").append(areas.sceneOffsetEnabled).append("\n");
    sb.append("snowEnabled: ").append(areas.snowEnabled).append("\n");
    sb.append("sandEnabled: ").append(areas.sandEnabled).append("\n");
    sb.append("fogEnabled: ").append(areas.fogEnabled).append("\n");
    sb.append("fogEnd: ").append(areas.fogEnd).append("\n");
    sb.append("fogShape: \"").append(areas.fogShape).append("\"\n");
    sb.append("sceneOffsetX: ").append(areas.sceneOffsetX).append("\n");
    sb.append("sceneOffsetY: ").append(areas.sceneOffsetY).append("\n");
    sb.append("sceneOffsetZ: ").append(areas.sceneOffsetZ).append("\n");
    sb.append("mustCopy: ").append(areas.mustCopy).append("\n");
    sb.append("mapName: \"").append(areas.mapName).append("\"\n");
    sb.append("weather: ").append(areas.weather).append("\n");
    sb.append("gravity: ").append(areas.gravity).append("\n");
    sb.append("effect: ").append(areas.effect.isEmpty() ? "(none)" : String.join(", ", areas.effect)).append("\n");
    sb.append("time: ").append(areas.time).append("\n");
    sb.append("daylightCycle: ").append(areas.daylightCycle).append("\n");
    sb.append("weatherCycle: ").append(areas.weatherCycle).append("\n");
    sb.append("minigameQuestEnabled: ").append(areas.minigameQuestEnabled).append("\n");
    sb.append("initialItems: ").append(areas.initialItems.isEmpty() ? "(none)" : String.join(", ", areas.initialItems)).append("\n");
    sb.append("disabledTasks: ").append(formatDisabledTasks(areas.disabledTasks)).append("\n");
    sb.append("disabledRoles: ").append(formatDisabledTasks(areas.disabledRoles)).append("\n");
    sb.append("enableSceneTask: ").append(formatDisabledTasks(areas.enableSceneTask));
    source.sendSuccess(
        () -> Component.literal(sb.toString()).withStyle(style -> style.withColor(ChatFormatting.AQUA)),
        false);
    return 1;
  }

  // ======================== 保存命令 ========================

  private static int executeSave(CommandSourceStack source, String mapName, boolean overwriteFile)
      throws CommandSyntaxException {
    ServerLevel serverWorld = source.getLevel();
    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
    if (gameComponent.isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return 1;
    }
    try {
      if (MapManager.saveCurrentMapWithoutTry(serverWorld, mapName, overwriteFile)) {
        source.sendSuccess(
            () -> Component.translatable("commands.sre.switchmap.save.success", mapName)
                .withStyle(style -> style.withColor(0x00FF00)),
            true);
      } else {
        source.sendFailure(Component.translatable("commands.sre.switchmap.error.save_failed", mapName));
      }
    } catch (Exception e) {
      throw ConfigCommand.createSimpleSyntaxException(e);
    }

    return 1;
  }

  private static LiteralArgumentBuilder<CommandSourceStack> buildMapTools() {
    return Commands.literal("map")
        .requires(source -> source.hasPermission(3))
        .then(Commands.literal("name")
            .then(Commands.argument("name", MapLoadArgumentType.string())
                .executes(context -> executeMapName(
                    context.getSource(),
                    StringArgumentType.getString(context, "name")))))
        .then(Commands.literal("save")
            .then(Commands.argument("mapName", MapLoadArgumentType.string())
                .executes(context -> executeSave(
                    context.getSource(),
                    StringArgumentType.getString(context, "mapName"),
                    false))
                .then(Commands.literal("force")
                    .executes(context -> executeSave(
                        context.getSource(),
                        StringArgumentType.getString(context, "mapName"),
                        true)))))
        .then(Commands.literal("load")
            .then(Commands.argument("mapName", MapLoadArgumentType.string())
                .executes(context -> executeMapLoad(
                    context.getSource(),
                    StringArgumentType.getString(context, "mapName")))))
        .then(Commands.literal("import")
            .then(Commands.argument("filename", StringArgumentType.string())
                .then(Commands.literal("as")
                    .then(Commands.argument("mapName", MapLoadArgumentType.string())
                        .executes(context -> executeMapImport(
                            context.getSource(),
                            StringArgumentType.getString(context, "filename"),
                            StringArgumentType.getString(context, "mapName"),
                            false))
                        .then(Commands.literal("force")
                            .executes(context -> executeMapImport(
                                context.getSource(),
                                StringArgumentType.getString(context, "filename"),
                                StringArgumentType.getString(context, "mapName"),
                                true)))))))
        .then(Commands.literal("list")
            .executes(context -> executeMapList(context.getSource())));
  }

  private static int executeMapName(CommandSourceStack source, String mapName) {
    if (!MapManager.isValidMapName(mapName)) {
      source.sendFailure(Component.literal("地图名称无效：禁止绝对路径、..、冒号，长度上限为 128"));
      return 0;
    }
    setMapName(source, mapName.endsWith(".json")
        ? mapName.substring(0, mapName.length() - 5)
        : mapName);
    return 1;
  }

  private static int executeMapLoad(CommandSourceStack source, String mapName) {
    if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return 0;
    }
    if (!MapManager.isValidMapName(mapName)) {
      source.sendFailure(Component.literal("地图名称无效"));
      return 0;
    }
    if (!MapManager.loadMap(source.getLevel(), mapName)) {
      source.sendFailure(Component.literal("无法载入地图配置: " + mapName));
      return 0;
    }
    source.sendSuccess(() -> Component.literal("已载入地图配置: " + mapName)
        .withStyle(style -> style.withColor(0x00FF00)), true);
    return 1;
  }

  private static int executeMapImport(CommandSourceStack source, String filename, String mapName,
      boolean overwrite) {
    if (SREGameWorldComponent.KEY.get(source.getLevel()).isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return 0;
    }
    MapManager.ImportResult result = MapManager.importMapConfig(
        source.getLevel(), filename, mapName, overwrite);
    if (!result.imported() || !result.loaded()) {
      source.sendFailure(Component.literal(result.message()));
      return 0;
    }
    source.sendSuccess(() -> Component.literal(result.message())
        .withStyle(style -> style.withColor(0x00FF00)), true);
    return 1;
  }

  private static int executeMapList(CommandSourceStack source) {
    List<String> maps = MapManager.getAvailableMaps(source.getLevel(), true);
    if (maps.isEmpty()) {
      source.sendSuccess(() -> Component.literal("train_maps 中没有地图配置"), false);
      return 1;
    }
    source.sendSuccess(() -> Component.literal("可用地图配置 (" + maps.size() + "):"), false);
    maps.forEach(map -> source.sendSuccess(() -> Component.literal(" - " + map), false));
    source.sendSuccess(() -> Component.literal("导入目录: <world>/map_imports"), false);
    return maps.size();
  }
  // ======================== 删除命令 ========================

  private static int executeRemove(CommandSourceStack source, String mapName)
      throws CommandSyntaxException {
    ServerLevel serverWorld = source.getLevel();
    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
    if (gameComponent.isRunning()) {
      source.sendFailure(Component.translatable("commands.sre.switchmap.error.game_running"));
      return 1;
    }
    try {
      if (MapManager.removeMapWithoutTry(serverWorld, mapName)) {
        source.sendSuccess(
            () -> Component.translatable("commands.sre.switchmap.remove.success", mapName)
                .withStyle(style -> style.withColor(0x00FF00)),
            true);
      } else {
        source.sendFailure(Component.translatable("commands.sre.switchmap.error.remove_failed", mapName));
      }
    } catch (Exception e) {
      throw ConfigCommand.createSimpleSyntaxException(e);
    }

    return 1;
  }

  // ======================== set 命令树构建 ========================

  private static LiteralArgumentBuilder<CommandSourceStack> setSpawnPos() {
    return Commands.literal("spawnPos")
        .then(Commands.argument("pos", Vec3Argument.vec3())
            .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                    .executes(ctx -> {
                      Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                      float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                      float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                      setSpawnPos(ctx.getSource(), pos, yaw, pitch);
                      return 1;
                    }))));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSpectatorSpawnPos() {
    return Commands.literal("spectatorSpawnPos")
        .then(Commands.argument("pos", Vec3Argument.vec3())
            .then(Commands.argument("yaw", FloatArgumentType.floatArg())
                .then(Commands.argument("pitch", FloatArgumentType.floatArg())
                    .executes(ctx -> {
                      Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                      float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                      float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                      setSpectatorSpawnPos(ctx.getSource(), pos, yaw, pitch);
                      return 1;
                    }))));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> buildSetAABB(
      String name,
      Function<AreasWorldComponent, AABB> getter,
      BiConsumer<AreasWorldComponent, AABB> setter) {

    return Commands.literal(name)
        // 完整设置：min <BlockPos> max <BlockPos>
        .then(Commands.literal("min")
            .then(Commands.argument("min", BlockPosArgument.blockPos())
                .then(Commands.literal("max")
                    .then(Commands.argument("max", BlockPosArgument.blockPos())
                        .executes(ctx -> {
                          CommandSourceStack src = ctx.getSource();
                          AreasWorldComponent areas = AreasWorldComponent.KEY.get(src.getLevel());
                          BlockPos min = BlockPosArgument.getBlockPos(ctx, "min");
                          BlockPos max = BlockPosArgument.getBlockPos(ctx, "max");
                          setAABBFull(areas, setter, min, max, src, name);
                          return 1;
                        })))
                // 仅设置 min 角
                .executes(ctx -> {
                  CommandSourceStack src = ctx.getSource();
                  AreasWorldComponent areas = AreasWorldComponent.KEY.get(src.getLevel());
                  BlockPos min = BlockPosArgument.getBlockPos(ctx, "min");
                  setAABBMin(areas, setter, getter, min, src, name);
                  return 1;
                })))
        // 仅设置 max 角
        .then(Commands.literal("max")
            .then(Commands.argument("max", BlockPosArgument.blockPos())
                .executes(ctx -> {
                  CommandSourceStack src = ctx.getSource();
                  AreasWorldComponent areas = AreasWorldComponent.KEY.get(src.getLevel());
                  BlockPos max = BlockPosArgument.getBlockPos(ctx, "max");
                  setAABBMax(areas, setter, getter, max, src, name);
                  return 1;
                })));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setWeather() {
    return Commands.literal("weather")
        .then(Commands.argument("value", StringArgumentType.string())
            .executes(ctx -> {
              setWeather(ctx.getSource(), StringArgumentType.getString(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setGravity() {
    return Commands.literal("gravity")
        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes(ctx -> {
              setGravity(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setEffect() {
    return Commands.literal("effect")
        .then(Commands.argument("value", StringArgumentType.string())
            .executes(ctx -> {
              setEffect(ctx.getSource(), StringArgumentType.getString(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setTime() {
    return Commands.literal("time")
        .then(Commands.argument("value", LongArgumentType.longArg())
            .executes(ctx -> {
              setTime(ctx.getSource(), LongArgumentType.getLong(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setDaylightCycle() {
    return Commands.literal("daylightCycle")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setDaylightCycle(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setWeatherCycle() {
    return Commands.literal("weatherCycle")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setWeatherCycle(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setMinigameQuestEnabled() {
    return Commands.literal("minigameQuestEnabled")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setMinigameQuestEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setInitialItems() {
    return Commands.literal("initialItems")
        .then(Commands.argument("value", StringArgumentType.string())
            .executes(ctx -> {
              setInitialItems(ctx.getSource(), StringArgumentType.getString(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setReadyArea() {
    return buildSetAABB("readyArea",
        AreasWorldComponent::getReadyArea,
        (a, box) -> a.setReadyArea(box));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setPlayArea() {
    return buildSetAABB("playArea",
        AreasWorldComponent::getPlayArea,
        (a, box) -> a.setPlayArea(box));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneArea() {
    return buildSetAABB("sceneArea",
        AreasWorldComponent::getSceneArea,
        AreasWorldComponent::setSceneArea);
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setResetTemplateArea() {
    return buildSetAABB("resetTemplateArea",
        AreasWorldComponent::getResetTemplateArea,
        (a, box) -> a.setResetTemplateArea(box));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setResetPasteArea() {
    return buildSetAABB("resetPasteArea",
        AreasWorldComponent::getResetPasteArea,
        (a, box) -> a.setResetPasteArea(box));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setPlayAreaOffset() {
    return Commands.literal("playAreaOffset")
        .then(Commands.argument("offset", Vec3Argument.vec3())
            .executes(ctx -> {
              Vec3 offset = Vec3Argument.getVec3(ctx, "offset");
              setPlayAreaOffset(ctx.getSource(), offset);
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setRoomCount() {
    return Commands.literal("roomCount")
        .then(Commands.argument("count", IntegerArgumentType.integer(1))
            .executes(ctx -> {
              setRoomCount(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setRoomPositions() {
    return Commands.literal("roomPositions")
        .then(Commands.literal("add")
            .then(Commands.argument("roomId", IntegerArgumentType.integer())
                .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(ctx -> {
                      int roomId = IntegerArgumentType.getInteger(ctx, "roomId");
                      Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                      addRoomPosition(ctx.getSource(), roomId, pos);
                      return 1;
                    }))))
        .then(Commands.literal("remove")
            .then(Commands.argument("roomId", IntegerArgumentType.integer())
                .executes(ctx -> {
                  removeRoomPosition(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "roomId"));
                  return 1;
                })));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setCanJump() {
    return Commands.literal("canJump")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setCanJump(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setCanSwim() {
    return Commands.literal("canSwim")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setCanSwim(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setEnableOxygenDrowning() {
    return Commands.literal("enableOxygenDrowning")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setEnableOxygenDrowning(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setMapStatusBar() {
    return Commands.literal("mapStatusBar")
        .then(Commands.argument("value", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              for (MapStatusBarType type : MapStatusBarType.values()) {
                String name = type.name().toLowerCase();
                if (name.startsWith(builder.getRemainingLowerCase())) {
                  builder.suggest(name);
                }
              }
              return builder.buildFuture();
            })
            .executes(ctx -> {
              setMapStatusBar(ctx.getSource(), MapStatusBarType.byName(StringArgumentType.getString(ctx, "value")));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setNoReset() {
    return Commands.literal("noReset")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setNoReset(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setHaveOutsideSound() {
    return Commands.literal("haveOutsideSound")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setHaveOutsideSound(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneOutsideSound() {
    return Commands.literal("sceneOutsideSound")
        .then(Commands.argument("value", StringArgumentType.word())
            .suggests((ctx, builder) -> {
              for (String s : new String[] { "train", "wind", "sand_storm", "snow_storm", "circus" }) {
                if (s.startsWith(builder.getRemainingLowerCase()))
                  builder.suggest(s);
              }
              return builder.buildFuture();
            })
            .executes(ctx -> {
              setSceneOutsideSound(ctx.getSource(), StringArgumentType.getString(ctx, "value"));
              return 1;
            }));
  }

  private static void setSceneOutsideSound(CommandSourceStack source, String value) {
    var areas = AreasWorldComponent.KEY.get(source.getLevel());
    areas.sceneOutsideSound = value;
    sendSetFeedback(source, "sceneOutsideSound", value);
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneOffsetEnabled() {
    return Commands.literal("sceneOffsetEnabled")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setSceneOffsetEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSnowEnabled() {
    return Commands.literal("snowEnabled")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setSnowEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSandEnabled() {
    return Commands.literal("sandEnabled")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setSandEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setFogEnabled() {
    return Commands.literal("fogEnabled")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setFogEnabled(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setFogEnd() {
    return Commands.literal("fogEnd")
        .then(Commands.argument("value", FloatArgumentType.floatArg(1, 10000))
            .executes(ctx -> {
              setFogEnd(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setFogShape() {
    return Commands.literal("fogShape")
        .then(Commands.argument("value", StringArgumentType.string())
            .suggests((ctx, builder) -> {
              builder.suggest("SPHERE");
              builder.suggest("CYLINDER");
              return builder.buildFuture();
            })
            .executes(ctx -> {
              setFogShape(ctx.getSource(), StringArgumentType.getString(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneOffsetX() {
    return Commands.literal("sceneOffsetX")
        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes(ctx -> {
              setSceneOffsetX(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneOffsetY() {
    return Commands.literal("sceneOffsetY")
        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes(ctx -> {
              setSceneOffsetY(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setSceneOffsetZ() {
    return Commands.literal("sceneOffsetZ")
        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
            .executes(ctx -> {
              setSceneOffsetZ(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setMustCopy() {
    return Commands.literal("mustCopy")
        .then(Commands.argument("value", BoolArgumentType.bool())
            .executes(ctx -> {
              setMustCopy(ctx.getSource(), BoolArgumentType.getBool(ctx, "value"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setMapName() {
    return Commands.literal("mapName")
        .then(Commands.argument("name", StringArgumentType.string())
            .executes(ctx -> {
              setMapName(ctx.getSource(), StringArgumentType.getString(ctx, "name"));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setDisabledTasks() {
    return Commands.literal("disabledTasks")
        .then(Commands.literal("add")
            .then(Commands.argument("taskId", StringArgumentType.string())
                .executes(ctx -> {
                  addDisabledTask(ctx.getSource(), StringArgumentType.getString(ctx, "taskId"));
                  return 1;
                })))
        .then(Commands.literal("remove")
            .then(Commands.argument("taskId", StringArgumentType.string())
                .executes(ctx -> {
                  removeDisabledTask(ctx.getSource(), StringArgumentType.getString(ctx, "taskId"));
                  return 1;
                })));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setDisabledRoles() {
    return Commands.literal("disabledRoles")
        .then(Commands.literal("add")
            .then(Commands.argument("roleId", StringArgumentType.string())
                .executes(ctx -> {
                  addDisabledRole(ctx.getSource(), StringArgumentType.getString(ctx, "roleId"));
                  return 1;
                })))
        .then(Commands.literal("remove")
            .then(Commands.argument("roleId", StringArgumentType.string())
                .executes(ctx -> {
                  removeDisabledRole(ctx.getSource(), StringArgumentType.getString(ctx, "roleId"));
                  return 1;
                })));
  }

  // enableSceneTask
  private static void addEnableSceneTask(CommandSourceStack source, String taskId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.enableSceneTask == null)
      areas.enableSceneTask = new HashSet<>();
    areas.enableSceneTask.add(taskId);
    areas.sync();
    sendSetFeedback(source, "enableSceneTask.add", taskId);
  }

  private static void removeEnableSceneTask(CommandSourceStack source, String taskId) {
    AreasWorldComponent areas = AreasWorldComponent.KEY.get(source.getLevel());
    if (areas.enableSceneTask != null && areas.enableSceneTask.remove(taskId)) {
      areas.sync();
      sendSetFeedback(source, "enableSceneTask.remove", taskId);
    } else {
      source.sendFailure(Component.literal("场景任务 " + taskId + " 不在启用列表中"));
    }
  }

  private static LiteralArgumentBuilder<CommandSourceStack> setEnableSceneTask() {
    return Commands.literal("enableSceneTask")
        .then(Commands.literal("add")
            .then(Commands.argument("taskId", StringArgumentType.string())
                .executes(ctx -> {
                  addEnableSceneTask(ctx.getSource(), StringArgumentType.getString(ctx, "taskId"));
                  return 1;
                })))
        .then(Commands.literal("remove")
            .then(Commands.argument("taskId", StringArgumentType.string())
                .executes(ctx -> {
                  removeEnableSceneTask(ctx.getSource(), StringArgumentType.getString(ctx, "taskId"));
                  return 1;
                })));
  }

  // ======================== get 命令树构建 ========================

  private static LiteralArgumentBuilder<CommandSourceStack> getSpawnPos() {
    return Commands.literal("spawnPos")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "spawnPos", formatPosWithOrientation(a.getSpawnPos()));
          return 1;
        });
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getSpectatorSpawnPos() {
    return Commands.literal("spectatorSpawnPos")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "spectatorSpawnPos", formatPosWithOrientation(a.getSpectatorSpawnPos()));
          return 1;
        });
  }

  private static LiteralArgumentBuilder<CommandSourceStack> buildGetAABB(
      String name, Function<AreasWorldComponent, AABB> getter) {
    return Commands.literal(name)
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), name, formatAABB(getter.apply(a)));
          return 1;
        })
        .then(Commands.literal("min")
            .executes(ctx -> {
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              sendGetFeedback(ctx.getSource(), name + ".min", formatAABBMin(getter.apply(a)));
              return 1;
            }))
        .then(Commands.literal("max")
            .executes(ctx -> {
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              sendGetFeedback(ctx.getSource(), name + ".max", formatAABBMax(getter.apply(a)));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getPlayAreaOffset() {
    return Commands.literal("playAreaOffset")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "playAreaOffset", formatVec3(a.getPlayAreaOffset()));
          return 1;
        });
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getRoomCount() {
    return Commands.literal("roomCount")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "roomCount", String.valueOf(a.getRoomCount()));
          return 1;
        });
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getRoomPositions() {
    return Commands.literal("roomPositions")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "roomPositions", formatRoomPositions(a.getRoomPositions()));
          return 1;
        })
        .then(Commands.argument("roomId", IntegerArgumentType.integer())
            .executes(ctx -> {
              int id = IntegerArgumentType.getInteger(ctx, "roomId");
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              Vec3 pos = a.getRoomPositions().get(id);
              if (pos == null) {
                ctx.getSource().sendFailure(Component.literal("房间 " + id + " 没有定义位置"));
              } else {
                sendGetFeedback(ctx.getSource(), "roomPositions." + id, formatVec3(pos));
              }
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> buildGetSimple(
      String name, Function<AreasWorldComponent, String> valueGetter) {
    return Commands.literal(name)
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), name, valueGetter.apply(a));
          return 1;
        });
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getDisabledTasks() {
    return Commands.literal("disabledTasks")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "disabledTasks", formatDisabledTasks(a.disabledTasks));
          return 1;
        })
        .then(Commands.argument("taskId", StringArgumentType.string())
            .executes(ctx -> {
              String taskId = StringArgumentType.getString(ctx, "taskId");
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              boolean has = a.disabledTasks != null && a.disabledTasks.contains(taskId);
              sendGetFeedback(ctx.getSource(), "disabledTasks.contains(" + taskId + ")", String.valueOf(has));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getDisabledRoles() {
    return Commands.literal("disabledRoles")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "disabledRoles", formatDisabledTasks(a.disabledRoles));
          return 1;
        })
        .then(Commands.argument("roleId", StringArgumentType.string())
            .executes(ctx -> {
              String roleId = StringArgumentType.getString(ctx, "roleId");
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              boolean has = a.disabledRoles != null && a.disabledRoles.contains(roleId);
              sendGetFeedback(ctx.getSource(), "disabledRoles.contains(" + roleId + ")", String.valueOf(has));
              return 1;
            }));
  }

  private static LiteralArgumentBuilder<CommandSourceStack> getEnableSceneTask() {
    return Commands.literal("enableSceneTask")
        .executes(ctx -> {
          AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
          sendGetFeedback(ctx.getSource(), "enableSceneTask", formatDisabledTasks(a.enableSceneTask));
          return 1;
        })
        .then(Commands.argument("taskId", StringArgumentType.string())
            .executes(ctx -> {
              String taskId = StringArgumentType.getString(ctx, "taskId");
              AreasWorldComponent a = AreasWorldComponent.KEY.get(ctx.getSource().getLevel());
              boolean has = a.enableSceneTask != null && a.enableSceneTask.contains(taskId);
              sendGetFeedback(ctx.getSource(), "enableSceneTask.contains(" + taskId + ")", String.valueOf(has));
              return 1;
            }));
  }
}
