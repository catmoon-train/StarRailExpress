package io.wifi.starrailexpress.content.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class MapManagerSettingsCommand {

  public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final int PAGE_SIZE = 10;

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("sre:area_manager")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("set")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .then(Commands.literal("add")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> executeSetAdd(ctx))))
                    .then(Commands.literal("remove")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> executeSetRemove(ctx))))
                    .then(Commands.literal("insert")
                        .then(Commands.argument("index", IntegerArgumentType.integer())
                            .then(Commands.argument("value", StringArgumentType.greedyString())
                                .executes(ctx -> executeSetInsert(ctx)))))
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> executeSetDirect(ctx)))))
            .then(Commands.literal("get")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .executes(ctx -> executeGet(ctx, 1))
                    .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeGet(ctx,
                            IntegerArgumentType.getInteger(ctx, "page")))))));
  }

  // ============ 执行方法 ============

  private static int executeSetDirect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    return executeSet(ctx, "value", false, null);
  }

  private static int executeSetAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    return executeSet(ctx, "value", true, "add");
  }

  private static int executeSetRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    return executeSet(ctx, "value", true, "remove");
  }

  private static int executeSetInsert(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    String path = StringArgumentType.getString(ctx, "path");
    int index = IntegerArgumentType.getInteger(ctx, "index");
    String valueStr = StringArgumentType.getString(ctx, "value");
    CommandSourceStack source = ctx.getSource();
    ServerLevel level = getLevel(source);
    AreasWorldComponent component = getComponent(level);
    Object root = getRoot(component);

    try {
      String[] pathParts = path.split("\\.");
      FieldAccess access = getFieldAccess(root, pathParts);
      Object collectionObj = access.field.get(access.owner);
      if (!(collectionObj instanceof List<?>)) {
        throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.not_list")).create();
      }
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) collectionObj;
      Class<?> elementType = getCollectionElementType(access.field);
      if (elementType == null) {
        if (!list.isEmpty()) {
          elementType = list.get(0).getClass();
        } else {
          elementType = Object.class;
        }
      }
      Object value = convertStringToValue(valueStr, elementType);
      int actualIndex = index;
      if (index < 0) {
        actualIndex = list.size() + index + 1;
      }
      if (actualIndex < 0 || actualIndex > list.size()) {
        throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.index_out_of_bounds"))
            .create();
      }
      list.add(actualIndex, value);
      component.sync();
      final int temp = actualIndex;
      source.sendSuccess(
          () -> Component.translatable("sre.area_manager.insert.success", temp, valueStr)
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      return 1;
    } catch (Exception e) {
      throw new SimpleCommandExceptionType(
          Component.translatable("sre.area_manager.error.operation_failed", e.getMessage())).create();
    }
  }

  private static int executeSet(CommandContext<CommandSourceStack> ctx, String valueArgName, boolean isAddOrRemove,
      String operation) throws CommandSyntaxException {
    String path = StringArgumentType.getString(ctx, "path");
    String valueStr = StringArgumentType.getString(ctx, valueArgName);
    CommandSourceStack source = ctx.getSource();
    ServerLevel level = getLevel(source);
    AreasWorldComponent component = getComponent(level);
    Object root = getRoot(component);

    try {
      String[] pathParts = path.split("\\.");
      FieldAccess access = getFieldAccess(root, pathParts);
      Object current = access.field.get(access.owner);

      if (isAddOrRemove) {
        if (!(current instanceof Collection<?>)) {
          throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.not_collection"))
              .create();
        }
        @SuppressWarnings("unchecked")
        Collection<Object> coll = (Collection<Object>) current;
        Class<?> elementType = getCollectionElementType(access.field);
        if (elementType == null) {
          if (!coll.isEmpty()) {
            elementType = coll.iterator().next().getClass();
          } else {
            elementType = Object.class;
          }
        }
        Object value = convertStringToValue(valueStr, elementType);
        if ("add".equals(operation)) {
          coll.add(value);
          source.sendSuccess(
              () -> Component.translatable("sre.area_manager.add.success", valueStr)
                  .withStyle(style -> style.withColor(0x00FF00)),
              true);
        } else { // remove
          boolean removed = coll.remove(value);
          if (removed) {
            source.sendSuccess(
                () -> Component.translatable("sre.area_manager.remove.success", valueStr)
                    .withStyle(style -> style.withColor(0x00FF00)),
                true);
          } else {
            throw new SimpleCommandExceptionType(
                Component.translatable("sre.area_manager.error.remove_not_found", valueStr)).create();
          }
        }
      } else {
        Class<?> fieldType = access.field.getType();
        Object value = convertStringToValue(valueStr, fieldType);
        access.field.set(access.owner, value);
        source.sendSuccess(
            () -> Component.translatable("sre.area_manager.set.success", path, valueStr)
                .withStyle(style -> style.withColor(0x00FF00)),
            true);
      }
      component.sync();
      return 1;
    } catch (Exception e) {
      throw new SimpleCommandExceptionType(
          Component.translatable("sre.area_manager.error.operation_failed", e.getMessage())).create();
    }
  }

  private static int executeGet(CommandContext<CommandSourceStack> ctx, int page) throws CommandSyntaxException {
    String path = StringArgumentType.getString(ctx, "path");
    CommandSourceStack source = ctx.getSource();
    ServerLevel level = getLevel(source);
    AreasWorldComponent component = getComponent(level);
    Object root = getRoot(component);

    try {
      String[] pathParts = path.split("\\.");
      Object target = getObjectByPath(root, pathParts);
      if (target == null) {
        source.sendSuccess(
            () -> Component.translatable("sre.area_manager.get.success", path, "null")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)),
            false);
        return 1;
      }

      if (target instanceof Collection<?>) {
        List<?> list = new ArrayList<>((Collection<?>) target);
        int total = list.size();
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (page < 1)
          page = 1;
        if (page > totalPages && totalPages > 0) {
          throw new SimpleCommandExceptionType(
              Component.translatable("sre.area_manager.error.page_out_of_range", totalPages)).create();
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);
        List<?> subList = list.subList(start, end);
        String items = subList.stream()
            .map(MapManagerSettingsCommand::objectToString)
            .collect(Collectors.joining(", "));
        final int temp = page;
        source.sendSuccess(
            () -> Component.translatable("sre.area_manager.get.page", temp, totalPages, total, items)
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)),
            false);
      } else {
        source.sendSuccess(
            () -> Component.translatable("sre.area_manager.get.success", path, objectToString(target))
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)),
            false);
      }
      return 1;
    } catch (Exception e) {
      throw new SimpleCommandExceptionType(
          Component.translatable("sre.area_manager.error.operation_failed", e.getMessage())).create();
    }
  }

  // ============ 核心反射工具 ============

  private static class FieldAccess {
    Object owner;
    Field field;

    FieldAccess(Object owner, Field field) {
      this.owner = owner;
      this.field = field;
    }
  }

  private static FieldAccess getFieldAccess(Object root, String[] path) throws Exception {
    if (path.length == 0)
      throw new IllegalArgumentException("路径为空");
    Object current = root;
    for (int i = 0; i < path.length - 1; i++) {
      Field field = current.getClass().getDeclaredField(path[i]);
      field.setAccessible(true);
      current = field.get(current);
      if (current == null) {
        throw new NullPointerException("字段 " + path[i] + " 为 null，无法继续");
      }
    }
    Field lastField = current.getClass().getDeclaredField(path[path.length - 1]);
    lastField.setAccessible(true);
    return new FieldAccess(current, lastField);
  }

  private static Object getObjectByPath(Object root, String[] path) throws Exception {
    Object current = root;
    for (String part : path) {
      Field field = current.getClass().getDeclaredField(part);
      field.setAccessible(true);
      current = field.get(current);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  private static Class<?> getCollectionElementType(Field field) {
    Type genericType = field.getGenericType();
    if (genericType instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) genericType;
      Type[] args = pt.getActualTypeArguments();
      if (args.length == 1) {
        Type arg = args[0];
        if (arg instanceof Class) {
          return (Class<?>) arg;
        } else if (arg instanceof ParameterizedType) {
          return (Class<?>) ((ParameterizedType) arg).getRawType();
        }
      }
    }
    return null;
  }

  // ============ 类型转换 ============

  private static Object convertStringToValue(String str, Class<?> targetType) {
    if (targetType == String.class)
      return str;
    if (targetType == Integer.class || targetType == int.class)
      return Integer.parseInt(str);
    if (targetType == Long.class || targetType == long.class)
      return Long.parseLong(str);
    if (targetType == Double.class || targetType == double.class)
      return Double.parseDouble(str);
    if (targetType == Float.class || targetType == float.class)
      return Float.parseFloat(str);
    if (targetType == Boolean.class || targetType == boolean.class)
      return Boolean.parseBoolean(str);
    if (targetType.isEnum()) {
      for (Object e : targetType.getEnumConstants()) {
        if (((Enum<?>) e).name().equalsIgnoreCase(str)) {
          return e;
        }
      }
      throw new IllegalArgumentException("Invalid enum constant: " + str);
    }
    String trimmed = str.trim();
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
      try {
        return GSON.fromJson(trimmed, targetType);
      } catch (JsonSyntaxException e) {
        throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
      }
    }
    throw new IllegalArgumentException("Unsupported type: " + targetType.getName() + ", use JSON object");
  }

  // ============ 对象转字符串 ============

  private static String objectToString(Object obj) {
    if (obj == null)
      return "null";
    if (obj instanceof Enum)
      return ((Enum<?>) obj).name();
    if (obj instanceof Collection) {
      return ((Collection<?>) obj).stream()
          .map(MapManagerSettingsCommand::objectToString)
          .collect(Collectors.joining(", ", "[", "]"));
    }
    if (obj instanceof Map) {
      return GSON.toJson(obj);
    }
    if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
      return obj.toString();
    }
    try {
      return GSON.toJson(obj);
    } catch (Exception e) {
      return obj.toString();
    }
  }

  // ============ 世界/组件/根对象获取辅助 ============

  private static ServerLevel getLevel(CommandSourceStack source) throws CommandSyntaxException {
    if (source.getLevel() instanceof ServerLevel) {
      return (ServerLevel) source.getLevel();
    }
    throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.world")).create();
  }

  private static AreasWorldComponent getComponent(ServerLevel level) throws CommandSyntaxException {
    AreasWorldComponent component = AreasWorldComponent.KEY.get(level);
    if (component == null) {
      throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.component")).create();
    }
    return component;
  }

  private static Object getRoot(AreasWorldComponent component) throws CommandSyntaxException {
    Object root = component.areasSettings;
    if (root == null) {
      throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.settings_null")).create();
    }
    return root;
  }

  // ============ 命令补全 ============

  private static final SuggestionProvider<CommandSourceStack> PATH_SUGGESTIONS = (ctx, builder) -> {
    CommandSourceStack source = ctx.getSource();
    ServerLevel level;
    try {
      level = getLevel(source);
    } catch (CommandSyntaxException e) {
      return builder.buildFuture();
    }
    AreasWorldComponent component;
    try {
      component = getComponent(level);
    } catch (CommandSyntaxException e) {
      return builder.buildFuture();
    }
    Object root;
    try {
      root = getRoot(component);
    } catch (CommandSyntaxException e) {
      return builder.buildFuture();
    }

    String input = builder.getRemaining();
    String[] parts = input.split("\\.");
    int lastIndex = parts.length - 1;
    String prefix = parts[lastIndex];
    String[] pathToParent = Arrays.copyOf(parts, lastIndex);

    try {
      Object current = root;
      for (String part : pathToParent) {
        Field field = current.getClass().getDeclaredField(part);
        field.setAccessible(true);
        current = field.get(current);
        if (current == null) {
          return builder.buildFuture();
        }
      }
      List<String> candidates = new ArrayList<>();
      for (Field field : current.getClass().getDeclaredFields()) {
        candidates.add(field.getName());
      }
      for (String name : candidates) {
        if (name.startsWith(prefix)) {
          String suggestion = input.substring(0, input.length() - prefix.length()) + name;
          builder.suggest(suggestion);
        }
      }
    } catch (Exception ignored) {
    }
    return builder.buildFuture();
  };
}