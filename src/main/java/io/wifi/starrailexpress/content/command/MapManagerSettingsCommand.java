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
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

public class MapManagerSettingsCommand {

  public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final int PAGE_SIZE = 10;

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(
        Commands.literal("sre:area_manager")
            .requires(source -> source.hasPermission(2))
            // set 直接赋值
            .then(Commands.literal("set")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(VALUE_SUGGESTIONS)
                        .executes(ctx -> executeSetDirect(ctx)))))
            // add 添加元素
            .then(Commands.literal("add")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(VALUE_SUGGESTIONS)
                        .executes(ctx -> executeSetAdd(ctx)))))
            // remove 移除元素
            .then(Commands.literal("remove")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .then(Commands.argument("value", StringArgumentType.greedyString())
                        .suggests(VALUE_SUGGESTIONS)
                        .executes(ctx -> executeSetRemove(ctx)))))
            // insert 插入元素
            .then(Commands.literal("insert")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .then(Commands.argument("index", IntegerArgumentType.integer())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .suggests(VALUE_SUGGESTIONS)
                            .executes(ctx -> executeSetInsert(ctx))))))
            // clear 清空集合
            .then(Commands.literal("clear")
                .then(Commands.argument("path", StringArgumentType.string())
                    .suggests(PATH_SUGGESTIONS)
                    .executes(ctx -> executeClear(ctx))))
            // get 查询值
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
                .withStyle(ChatFormatting.AQUA),
            false);
        return 1;
      }

      if (target instanceof Collection<?>) {
        List<?> list = new ArrayList<>((Collection<?>) target);
        int total = list.size();
        if (total == 0) {
          source.sendSuccess(
              () -> Component.translatable("sre.area_manager.get.empty", path)
                  .withStyle(ChatFormatting.AQUA),
              false);
          return 1;
        }
        int totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (page < 1)
          page = 1;
        if (page > totalPages) {
          throw new SimpleCommandExceptionType(
              Component.translatable("sre.area_manager.error.page_out_of_range", totalPages)).create();
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, total);
        List<?> subList = list.subList(start, end);

        MutableComponent result = Component.literal("");
        // 头部：路径、总项数、当前页码
        final int temp = page;
        result.append(Component.translatable("sre.area_manager.get.header", path, total, temp, totalPages)
            .withStyle(ChatFormatting.AQUA))
            .append("\n");

        // 每个元素一行，灰色
        for (Object item : subList) {
          result.append(objectToComponent(path, item).copy()
              .withStyle(ChatFormatting.GRAY))
              .append("\n");
        }

        // 翻页导航（总页数 > 1 时显示）
        if (totalPages > 1) {
          MutableComponent nav = Component.literal("");
          if (page > 1) {
            nav.append(Component.literal("[首页]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/sre:area_manager get " + path + " 1"))))
                .append(" ");
            nav.append(Component.literal("[上一页]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/sre:area_manager get " + path + " " + (temp - 1)))))
                .append(" ");
          }
          nav.append(Component.literal(page + "/" + totalPages)
              .withStyle(ChatFormatting.AQUA));
          if (page < totalPages) {
            nav.append(" ")
                .append(Component.literal("[下一页]")
                    .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            "/sre:area_manager get " + path + " " + (temp + 1)))))
                .append(" ");
            nav.append(Component.literal("[尾页]")
                .withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/sre:area_manager get " + path + " " + totalPages))));
          }
          result.append(nav);
        }

        final MutableComponent finalResult = result;
        source.sendSuccess(() -> finalResult, false);
        return 1;
      } else {
        // 非集合类型的原逻辑不变
        source.sendSuccess(
            () -> Component.translatable("sre.area_manager.get.success", path, objectToComponent(path, target))
                .withStyle(ChatFormatting.AQUA),
            false);
        return 1;
      }
    } catch (Exception e) {
      throw new SimpleCommandExceptionType(
          Component.translatable("sre.area_manager.error.operation_failed", e.getMessage())).create();
    }
  }

  // ============ 新增 clear 执行 ============
  private static int executeClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
    String path = StringArgumentType.getString(ctx, "path");
    CommandSourceStack source = ctx.getSource();
    ServerLevel level = getLevel(source);
    AreasWorldComponent component = getComponent(level);
    Object root = getRoot(component);

    try {
      String[] pathParts = path.split("\\.");
      FieldAccess access = getFieldAccess(root, pathParts);
      Object collectionObj = access.field.get(access.owner);
      if (!(collectionObj instanceof Collection<?>)) {
        throw new SimpleCommandExceptionType(Component.translatable("sre.area_manager.error.not_collection"))
            .create();
      }
      Collection<?> coll = (Collection<?>) collectionObj;
      coll.clear();
      component.sync();
      source.sendSuccess(
          () -> Component.translatable("sre.area_manager.clear.success", path)
              .withStyle(ChatFormatting.GREEN),
          true);
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

  private static Component objectToComponent(String path, Object obj) {
    if (obj == null)
      return Component.literal("null");
    if (obj instanceof Enum)
      return warpEnum(path, ((Enum<?>) obj).name());
    if (obj instanceof Collection) {
      MutableComponent result = Component.literal("[");
      boolean first = true;
      for (Object o : (Collection<?>) obj) {
        if (!first) {
          result.append(Component.literal(", "));
        }
        result.append(MapManagerSettingsCommand.objectToComponent(path, o));
        first = false;
      }
      result.append(Component.literal("]"));
      return result;
    }
    if (obj instanceof Map) {
      return Component.literal(GSON.toJson(obj));
    }
    if (obj instanceof String || obj instanceof Number || obj instanceof Boolean) {
      return Component.literal(obj.toString());
    }
    try {
      return Component.literal(GSON.toJson(obj));
    } catch (Exception e) {
      return Component.literal(obj.toString());
    }
  }

  // ============ 世界/组件/根对象获取辅助 ============

  private static Component warpEnum(String path, String name) {
    return Component.translatable("%s (%s)", name,
        Component.translatableWithFallback("sre.map_helper.settings." + path + "." + name, name));
  }

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

  // ============ 新增 value 参数补全 ============

  private static final SuggestionProvider<CommandSourceStack> VALUE_SUGGESTIONS = (ctx, builder) -> {
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
    String path;
    try {
      path = ctx.getArgument("path", String.class);
    } catch (IllegalArgumentException e) {
      return builder.buildFuture();
    }

    String[] pathParts = path.split("\\.");
    try {
      FieldAccess access = getFieldAccess(root, pathParts);
      Field field = access.field;
      Class<?> fieldType = field.getType();

      if (Collection.class.isAssignableFrom(fieldType)) {
        // 集合类型：建议 [] 以及元素类型的默认值
        builder.suggest("[]");
        Class<?> elementType = getCollectionElementType(field);
        if (elementType == null) {
          // 如果无法获取泛型，尝试从集合实例获取，但补全时可能为空，故用 Object
          elementType = Object.class;
        }
        addDefaultSuggestions(builder, elementType);
      } else {
        // 非集合：直接根据字段类型建议
        addDefaultSuggestions(builder, fieldType);
      }
    } catch (Exception ignored) {
    }
    return builder.buildFuture();
  };

  private static void addDefaultSuggestions(SuggestionsBuilder builder, Class<?> type) {
    if (type.isEnum()) {
      for (Object constant : type.getEnumConstants()) {
        builder.suggest(((Enum<?>) constant).name());
      }
    } else if (type == String.class) {
      builder.suggest("example");
      builder.suggest("test");
    } else if (type == Integer.class || type == int.class) {
      builder.suggest("0");
      builder.suggest("1");
    } else if (type == Long.class || type == long.class) {
      builder.suggest("0");
      builder.suggest("1");
    } else if (type == Double.class || type == double.class) {
      builder.suggest("0.0");
      builder.suggest("1.0");
    } else if (type == Float.class || type == float.class) {
      builder.suggest("0.0");
      builder.suggest("1.0");
    } else if (type == Boolean.class || type == boolean.class) {
      builder.suggest("true");
      builder.suggest("false");
    } else {
      // 复杂对象，建议 JSON 对象和数组
      builder.suggest("{}");
      builder.suggest("[]");
    }
  }
}