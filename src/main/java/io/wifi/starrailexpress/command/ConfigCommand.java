package io.wifi.starrailexpress.command;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import io.wifi.ConfigCompact.ConfigClassHandler;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.agmas.harpymodloader.config.HarpyModLoaderConfig;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.RoleShopHandler;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

public class ConfigCommand {
  static Gson gson = new Gson();

  public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
    dispatcher.register(Commands.literal("tmm:config")
        .requires(source -> source.hasPermission(2))
        .executes(ConfigCommand::showConfig)
        .then(Commands.literal("config")
            .then(Commands.argument("config", StringArgumentType.string())
                .suggests(ConfigCommand::suggestConfigNames)
                .then(Commands.argument("entry", StringArgumentType.string())
                    .suggests(ConfigCommand::suggestConfigEntry)
                    .then(Commands.literal("get").executes(ConfigCommand::viewConfigEntry))
                    .then(Commands.literal("set")
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ConfigCommand::changeConfigEntry))))))
        .then(Commands.literal("reload")
            .executes(ConfigCommand::reloadConfig))
        .then(Commands.literal("auto_present")
            .then(Commands.argument("flag", BoolArgumentType.bool())
                .executes(ConfigCommand::autoPresent)))
        .then(Commands.literal("set_round")
            .then(Commands.argument("round", IntegerArgumentType.integer(0))
                .executes(ConfigCommand::setRound)))
        .then(Commands.literal("reset")
            .executes(ConfigCommand::resetConfig)));
  }

  private static CompletableFuture<Suggestions> suggestConfigNames(CommandContext<CommandSourceStack> context,
      SuggestionsBuilder builder) {
    Set<String> suggestions = new HashSet<>();
    // 添加自定义 ID 到 Set

    String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
    ConfigClassHandler.configNameToClassMap.keySet().stream()
        .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
        .forEach(suggestions::add);
    // 最后批量建议
    suggestions.forEach((s) -> {
      builder.suggest(s);
    });
    return builder.buildFuture();
  }

  public static CommandSyntaxException createSimpleSyntaxException(Exception e) {
    return new CommandSyntaxException(new SimpleCommandExceptionType(new LiteralMessage("config")),
        new LiteralMessage(e.getMessage()));
  }

  private static CompletableFuture<Suggestions> suggestConfigEntry(CommandContext<CommandSourceStack> context,
      SuggestionsBuilder builder) throws CommandSyntaxException {
    String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
    String configName = StringArgumentType.getString(context, "config");
    Class<?> configClazz = ConfigClassHandler.configNameToClassMap.get(configName);
    if (configClazz == null) {
      return builder.buildFuture();
    }
    Object target = null;
    try {
      @SuppressWarnings("unchecked")
      var tt = (Class<ConfigData>) configClazz;
      target = ConfigClassHandler.instance(tt);
    } catch (Exception e) {
      throw createSimpleSyntaxException(e);

    }
    HashSet<String> entries = new HashSet<>();
    for (Field field : configClazz.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue; // 跳过静态字段
      }
      if (field.canAccess(target)) {
        try {
          String fieldName = field.getName();
          if (fieldName.toLowerCase().contains(remaining))
            entries.add(fieldName);
        } catch (Exception e) {
        }
      }
    }
    entries.forEach((s) -> {
      builder.suggest(s, getConfigDescription(configName, s));
    });
    return builder.buildFuture();
  }

  private static MutableComponent getConfigDescription(String configName, String entryName) {
    String baseId = "text.autoconfig." + configName + ".option." + entryName;
    var base = Component.translatable(baseId);
    if (Language.getInstance().has(baseId + ".@Tooltip")) {
      base.withStyle(style -> style
          .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(baseId + ".@Tooltip"))));
    } else if (Language.getInstance().has(baseId + ".@Tooltip[0]")) {
      var hover = Component.translatable(baseId + ".@Tooltip[0]");
      int idx = 1;
      while (Language.getInstance().has(baseId + ".@Tooltip[" + idx + "]")) {
        hover.append(Component.translatable(baseId + ".@Tooltip[" + idx + "]"));
        idx++;
      }
      base.withStyle(style -> style
          .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
    return base;
  }

  private static int viewConfigEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    String configName = StringArgumentType.getString(context, "config");
    Class<?> configClazz = ConfigClassHandler.configNameToClassMap.get(configName);
    String entryName = StringArgumentType.getString(context, "entry");
    if (configClazz == null) {
      throw createSimpleSyntaxException(new Exception("Config not found: " + configName));
    }
    Object target = null;
    try {
      @SuppressWarnings("unchecked")
      var tt = (Class<ConfigData>) configClazz;
      target = ConfigClassHandler.instance(tt);
    } catch (Exception e) {
      throw createSimpleSyntaxException(new Exception("Cannot find entry " + entryName));
    }
    Field field;
    try {
      field = configClazz.getDeclaredField(entryName);

      if (!field.canAccess(target) || Modifier.isStatic(field.getModifiers())) {
        throw createSimpleSyntaxException(new Exception("Cannot access field " + entryName + "!"));
      }
      var content = field.get(target);
      String str_content = gson.toJson(content);
      context.getSource().sendSuccess(
          () -> Component
              .translatable("Value of '%s': %s\n(Desc: %s)", entryName,
                  Component.literal(str_content).withStyle(ChatFormatting.WHITE).withStyle(style -> style
                      .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, str_content))
                      .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                          Component.literal("Click to copy").withStyle(ChatFormatting.AQUA)))),
                  getConfigDescription(configName, entryName).withStyle(ChatFormatting.GRAY))
              .withStyle(ChatFormatting.GREEN),
          false);
    } catch (Exception e) {
      throw createSimpleSyntaxException(e);
    }
    return 1;
  }

  private static int changeConfigEntry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
    String configName = StringArgumentType.getString(context, "config");
    Class<?> configClazz = ConfigClassHandler.configNameToClassMap.get(configName);
    String entryName = StringArgumentType.getString(context, "entry");
    String value = StringArgumentType.getString(context, "value");
    if (configClazz == null) {
      throw createSimpleSyntaxException(new Exception("Config not found: " + configName));
    }
    Object target = null;
    ConfigHolder<ConfigData> handler = null;
    try {
      @SuppressWarnings("unchecked")
      var tt = (Class<ConfigData>) configClazz;
      handler = ConfigClassHandler.handler(tt);
      target = handler.getConfig();
    } catch (Exception e) {
      throw createSimpleSyntaxException(e);
    }
    Field field;
    try {
      field = configClazz.getDeclaredField(entryName);

      if (!field.canAccess(target) || Modifier.isStatic(field.getModifiers())) {
        throw createSimpleSyntaxException(new Exception("Cannot access field " + entryName + "!"));
      }
      Class<?> fieldType = field.getType();
      Object trueValue = gson.fromJson(value, fieldType);
      field.set(target, trueValue);
      context.getSource().sendSuccess(
          () -> Component.translatable("Set value of '%s' to: %s\n(Desc: %s)", entryName, value,
              getConfigDescription(configName, entryName)),
          true);
      handler.save();
    } catch (Exception e) {
      throw createSimpleSyntaxException(e);
    }
    return 1;
  }

  private static int autoPresent(CommandContext<CommandSourceStack> context) {
    boolean flag = BoolArgumentType.getBool(context, "flag");
    CommandSourceStack source = context.getSource();
    SREConfig.instance().enableRoundBasedAutoPreset = flag;
    SREConfig.HANDLER.save();
    source.sendSuccess(() -> Component.literal("Set enableRoundBasedAutoPreset to " + (flag ? "True" : "False")),
        true);
    return 1;
  }

  private static int setRound(CommandContext<CommandSourceStack> context) {
    int round = IntegerArgumentType.getInteger(context, "round");
    CommandSourceStack source = context.getSource();
    SREConfig.instance().roundBasedCurrentRound = round;
    SREConfig.HANDLER.save();
    source.sendSuccess(() -> Component.literal("Set roundBasedCurrentRound to " + (round)),
        true);
    return 1;
  }

  private static int reloadConfig(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();
    try {
      SREConfig.instance().reload();
      SREConfig.HANDLER.syncToClient(source.getServer());
      HarpyModLoaderConfig.HANDLER.load();
      NoellesRolesConfig.HANDLER.load();
      StupidExpressConfig.HANDLER.load();
      StupidExpressConfig.HANDLER.syncToClient(source.getServer());
      RoleShopHandler.shopRegister();
      source.sendSuccess(
          () -> Component.translatable("commands.sre.config.reload")
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      SRE.LOGGER.info("Reloaded config by {}", source.getTextName());
      SRE.initConstants();
      return 1;
    } catch (Exception e) {
      source.sendFailure(Component.translatable("commands.sre.config.reload.fail", e.getMessage()));
      SRE.LOGGER.error("配置重载失败", e);
      return 0;
    }
  }

  private static int resetConfig(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();

    try {
      SREConfig.instance().reset();
      source.sendSuccess(
          () -> Component.translatable("commands.sre.config.reset")
              .withStyle(style -> style.withColor(0x00FF00)),
          true);
      SRE.LOGGER.info("配置文件已由 {} 重置为默认值", source.getTextName());
      return 1;
    } catch (Exception e) {
      source.sendFailure(Component.translatable("commands.sre.config.reset.fail", e.getMessage()));
      SRE.LOGGER.error("配置重置失败", e);
      return 0;
    }
  }

  private static int showConfig(CommandContext<CommandSourceStack> context) {
    CommandSourceStack source = context.getSource();

    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.header"), false);

    // 商店价格
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.header"), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.knife",
            SREConfig.instance().knifePrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.revolver",
            SREConfig.instance().revolverPrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.grenade",
            SREConfig.instance().grenadePrice),
        false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.psycho_mode",
        SREConfig.instance().psychoModePrice), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.poison_vial",
        SREConfig.instance().poisonVialPrice), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.scorpion",
            SREConfig.instance().scorpionPrice),
        false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.shop_prices.firecracker",
        SREConfig.instance().firecrackerPrice), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.lockpick",
            SREConfig.instance().lockpickPrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.crowbar",
            SREConfig.instance().crowbarPrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.body_bag",
            SREConfig.instance().bodyBagPrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.blackout",
            SREConfig.instance().blackoutPrice),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.shop_prices.note",
            SREConfig.instance().notePrice),
        false);

    // 物品冷却时间
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.header"), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.knife",
            SREConfig.instance().knifeCooldown),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.revolver",
            SREConfig.instance().revolverCooldown),
        false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.derringer",
        SREConfig.instance().derringerCooldown), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.grenade",
            SREConfig.instance().grenadeCooldown),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.lockpick",
            SREConfig.instance().lockpickCooldown),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.crowbar",
            SREConfig.instance().crowbarCooldown),
        false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.body_bag",
            SREConfig.instance().bodyBagCooldown),
        false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.cooldowns.psycho_mode",
        SREConfig.instance().psychoModeCooldown), false);
    source.sendSuccess(
        () -> Component.translatable("commands.sre.config.show.cooldowns.blackout",
            SREConfig.instance().blackoutCooldown),
        false);

    // 游戏设置
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.header"), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.starting_money",
        SREConfig.instance().startingMoney), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.passive_money_amount",
        SREConfig.instance().passiveMoneyAmount), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.passive_money_interval",
        SREConfig.instance().passiveMoneyInterval), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.money_per_kill",
        SREConfig.instance().moneyPerKill), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.psycho_mode_armor",
        SREConfig.instance().psychoModeArmor), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.psycho_mode_duration",
        SREConfig.instance().psychoModeDuration), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.firecracker_duration",
        SREConfig.instance().firecrackerDuration), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.game_settings.blackout_max_duration",
        SREConfig.instance().blackoutMaxDuration), false);

    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.footer"), false);
    source.sendSuccess(() -> Component.translatable("commands.sre.config.show.hint"), false);

    return 1;
  }
}