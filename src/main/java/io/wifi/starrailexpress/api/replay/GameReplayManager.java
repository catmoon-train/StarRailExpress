package io.wifi.starrailexpress.api.replay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.fabricmc.loader.api.FabricLoader.getInstance;

public class GameReplayManager implements IGameReplayRecorder {
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private static final String REPLAY_FILE_NAME = "game_replay.json";

  public GameReplayData currentReplayData;
  private final MinecraftServer server;
  public static final Map<UUID, String> playerNames = new HashMap<>();
  private GameReplay currentReplay;
  private ReplaySession session;
  private ReplayRecorder recorder;
  private ReplayFormatter formatter;
  private ReplayStorage storage;

  public GameReplayManager(MinecraftServer server) {
    this.server = server;
    this.currentReplayData = new GameReplayData();
    // this.playerNames = new HashMap<>();
    this.currentReplay = new GameReplay(0, GameUtils.WinStatus.NONE, new java.util.ArrayList<>(),
        new java.util.ArrayList<>());
    this.session = new ReplaySession(server, this.currentReplayData);
    this.recorder = new ReplayRecorder(this.session);
    this.formatter = new ReplayFormatter(this);
    this.storage = new ReplayStorage(server);
  }

  public void resetReplay() {
    this.currentReplayData = new GameReplayData();
    // this.playerNames.clear();
    this.currentReplay = new GameReplay(0, GameUtils.WinStatus.NONE, new java.util.ArrayList<>(),
        new java.util.ArrayList<>());
    this.session.reset(this.currentReplayData);
  }

  private ReplayEventTypes.EventType mapEventType(GameReplayData.EventType dataEventType) {
    return switch (dataEventType) {
      // 主要事件
      case PLAYER_REVIVAL -> ReplayEventTypes.EventType.PLAYER_REVIVAL;
      case PLAYER_KILL -> ReplayEventTypes.EventType.PLAYER_KILL;
      case PLAYER_POISONED -> ReplayEventTypes.EventType.PLAYER_POISONED;
      case GRENADE_THROWN -> ReplayEventTypes.EventType.GRENADE_THROWN;
      case SKILL_USED -> ReplayEventTypes.EventType.ITEM_USED;
      case ITEM_USED -> ReplayEventTypes.EventType.ITEM_USED;
      // 系统事件
      case GAME_START -> ReplayEventTypes.EventType.GAME_START;
      case GAME_END -> ReplayEventTypes.EventType.GAME_END;
      case ROLE_ASSIGNMENT -> ReplayEventTypes.EventType.GAME_START;
      case PLAYER_JOIN -> ReplayEventTypes.EventType.PLAYER_JOIN;
      case PLAYER_LEAVE -> ReplayEventTypes.EventType.PLAYER_LEAVE;
      case ARMOR_BREAK -> ReplayEventTypes.EventType.ARMOR_BREAK;
      // 次要事件
      case DOOR_OPEN -> ReplayEventTypes.EventType.DOOR_OPEN;
      case DOOR_CLOSE -> ReplayEventTypes.EventType.DOOR_CLOSE;
      case DOOR_LOCK -> ReplayEventTypes.EventType.DOOR_LOCK;
      case DOOR_UNLOCK -> ReplayEventTypes.EventType.DOOR_UNLOCK;
      case LOCKPICK_ATTEMPT -> ReplayEventTypes.EventType.LOCKPICK_ATTEMPT;
      case TASK_COMPLETE -> ReplayEventTypes.EventType.TASK_COMPLETE;
      case STORE_BUY -> ReplayEventTypes.EventType.STORE_BUY;
      case MOOD_CHANGE -> ReplayEventTypes.EventType.MOOD_CHANGE;
      case CHANGE_ROLE -> ReplayEventTypes.EventType.CHANGE_ROLE;
      case PSYCHO_STATE_CHANGE -> ReplayEventTypes.EventType.PSYCHO_STATE_CHANGE;
      case BLACKOUT_START -> ReplayEventTypes.EventType.BLACKOUT_START;
      case BLACKOUT_END -> ReplayEventTypes.EventType.BLACKOUT_END;
      // 默认映射
      case CUSTOM_MESSAGE -> ReplayEventTypes.EventType.CUSTOM_EVENT;
    };
  }

  private ReplayEvent convertReplayEvent(GameReplayData.ReplayEvent dataEvent, HolderLookup.Provider provider) {
    if (dataEvent == null) {
      return new ReplayEvent(ReplayEventTypes.EventType.GAME_START, 0, new ReplayEventTypes.EventDetails() {
      });
    }

    ReplayEventTypes.EventType eventType = mapEventType(dataEvent.getType());
    ReplayEventTypes.EventDetails details = switch (dataEvent.getType()) {
      // 主要事件
      case PLAYER_JOIN, PLAYER_LEAVE -> {
        String message = dataEvent.getMessage();
        var ply = dataEvent.getSourcePlayer();
        yield new ReplayEventTypes.PlayerJoinLeaveDetails(ply, message);
      }
      case PLAYER_KILL -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "minecraft:air";
        yield new ReplayEventTypes.PlayerKillDetails(dataEvent.getSourcePlayer(), dataEvent.getTargetPlayer(),
            ResourceLocation.parse(itemUsed));
      }
      case PLAYER_POISONED ->
        new ReplayEventTypes.PlayerPoisonedDetails(dataEvent.getSourcePlayer(), dataEvent.getTargetPlayer());
      case GRENADE_THROWN -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "0";
        BlockPos pos = BlockPos.of(Long.parseLong(itemUsed));
        yield new ReplayEventTypes.GrenadeThrownDetails(dataEvent.getSourcePlayer(), pos);
      }
      case SKILL_USED -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "minecraft:air";
        yield new ReplayEventTypes.ItemUsedDetails(dataEvent.getSourcePlayer(),
            ResourceLocation.parse(itemUsed));
      }
      case ITEM_USED -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "minecraft:air";
        yield new ReplayEventTypes.ItemUsedDetails(dataEvent.getSourcePlayer(),
            ResourceLocation.parse(itemUsed));
      }
      // 次要事件
      case LOCKPICK_ATTEMPT -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "0";
        BlockPos pos = BlockPos.of(Long.parseLong(itemUsed));
        String message = dataEvent.getMessage();
        boolean success = message != null ? Boolean.parseBoolean(message) : false;
        yield new ReplayEventTypes.LockpickAttemptDetails(dataEvent.getSourcePlayer(), pos, success);
      }
      case TASK_COMPLETE -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "minecraft:air";
        yield new ReplayEventTypes.TaskCompleteDetails(dataEvent.getSourcePlayer(),
            ResourceLocation.parse(itemUsed));
      }
      case STORE_BUY -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "minecraft:air:1";
        int lastColon = itemUsed.lastIndexOf(':');
        if (lastColon == -1) {
          // 如果没有冒号，假设数量为1
          yield new ReplayEventTypes.StoreBuyDetails(dataEvent.getSourcePlayer(),
              ResourceLocation.parse(itemUsed), 1);
        } else {
          String itemId = itemUsed.substring(0, lastColon);
          String amountStr = itemUsed.substring(lastColon + 1);
          try {
            int amount = Integer.parseInt(amountStr);
            yield new ReplayEventTypes.StoreBuyDetails(dataEvent.getSourcePlayer(),
                ResourceLocation.parse(itemId), amount);
          } catch (NumberFormatException e) {
            // 如果解析失败，使用默认数量1
            yield new ReplayEventTypes.StoreBuyDetails(dataEvent.getSourcePlayer(),
                ResourceLocation.parse(itemId), 1);
          }
        }
      }
      case MOOD_CHANGE -> {
        String message = dataEvent.getMessage();
        if (message == null)
          message = "0:0";
        String[] parts = message.split(":");
        if (parts.length < 2)
          parts = new String[] { "0", "0" };
        yield new ReplayEventTypes.MoodChangeDetails(dataEvent.getSourcePlayer(), Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]));
      }
      case DOOR_LOCK, DOOR_UNLOCK -> {
        String itemUsed = dataEvent.getItemUsed();
        if (itemUsed == null)
          itemUsed = "0";
        BlockPos pos = BlockPos.of(Long.parseLong(itemUsed));
        yield new ReplayEventTypes.DoorActionDetails(dataEvent.getSourcePlayer(), pos, true);
      }
      case PSYCHO_STATE_CHANGE -> {
        String message = dataEvent.getMessage();
        if (message == null)
          message = "0:0";
        String[] parts = message.split(":");
        if (parts.length < 2)
          parts = new String[] { "0", "0" };
        yield new ReplayEventTypes.PsychoStateChangeDetails(dataEvent.getSourcePlayer(),
            Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      }
      case BLACKOUT_START, BLACKOUT_END -> {
        String message = dataEvent.getMessage();
        long duration = message != null ? Long.parseLong(message) : 0L;
        yield new ReplayEventTypes.BlackoutEventDetails(duration);
      }
      case PLAYER_REVIVAL -> {
        String str_arr = dataEvent.getMessage();
        yield new ReplayEventTypes.PlayerRevivalDetails(dataEvent.getSourcePlayer(), str_arr);
      }
      case ARMOR_BREAK -> {
        yield new ReplayEventTypes.ArmorBreakDetails(dataEvent.getSourcePlayer());
      }

      case CHANGE_ROLE -> {
        String[] str_arr = dataEvent.getMessage().split("===");

        if (str_arr.length == 2) {
          yield new ReplayEventTypes.ChangeRoleDetails(dataEvent.getSourcePlayer(), str_arr[0], str_arr[1]);
        } else {
          if (str_arr.length >= 1) {
            yield new ReplayEventTypes.ChangeRoleDetails(dataEvent.getSourcePlayer(), "",
                dataEvent.getMessage());
          } else
            yield null;
        }
      }
      case CUSTOM_MESSAGE -> {
        String msg = dataEvent.getMessage();
        Component result = null;
        if (provider == null)
          if (SRE.SERVER != null)
            provider = SRE.SERVER.registryAccess();
        if (provider != null && msg != null) {
          try {
            result = Component.Serializer.fromJson(msg, provider);
          } catch (Exception e) {
            SRE.LOGGER.info(msg);
            e.printStackTrace();
          }
        }
        yield new ReplayEventTypes.CustomEventDetails(result);
      }
      // 默认空详情
      default -> new ReplayEventTypes.EventDetails() {
      };
    };

    return new ReplayEvent(eventType, dataEvent.getTimestamp(), details);
  }

  public void initializeReplay(List<ServerPlayer> players, HashMap<UUID, SRERole> roles) {
    resetReplay();
    updateReplayInitialRoles(players, roles);
  }

  public void updateReplayInitialRoles(List<ServerPlayer> players, HashMap<UUID, SRERole> roles) {
    for (ServerPlayer player : players) {
      recordPlayerName(player);
    }
    currentReplayData.setPlayerCount(players.size());
    // Set roles based on the provided HashMap
    currentReplayData.setCivilianPlayers(roles.entrySet().stream()
        .filter(entry -> entry.getValue().identifier()
            .equals(io.wifi.starrailexpress.api.TMMRoles.CIVILIAN.identifier()))
        .map(Map.Entry::getKey)
        .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll));
    currentReplayData.setKillerPlayers(roles.entrySet().stream()
        .filter(entry -> entry.getValue().identifier()
            .equals(io.wifi.starrailexpress.api.TMMRoles.KILLER.identifier()))
        .map(Map.Entry::getKey)
        .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll));
    currentReplayData.setVigilantePlayers(roles.entrySet().stream()
        .filter(entry -> entry.getValue().identifier()
            .equals(io.wifi.starrailexpress.api.TMMRoles.VIGILANTE.identifier()))
        .map(Map.Entry::getKey)
        .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll));
    currentReplayData.setLooseEndPlayers(roles.entrySet().stream()
        .filter(entry -> entry.getValue().identifier()
            .equals(io.wifi.starrailexpress.api.TMMRoles.LOOSE_END.identifier()))
        .map(Map.Entry::getKey)
        .collect(java.util.ArrayList::new, java.util.ArrayList::add, java.util.ArrayList::addAll));

    // 填充玩家角色映射
    Map<UUID, String> roleMap = new HashMap<>();
    for (Map.Entry<UUID, SRERole> entry : roles.entrySet()) {
      roleMap.put(entry.getKey(), entry.getValue().identifier().toString());
      // 确保所有玩家的名称都被记录，即使他们尚未在游戏中被显式记录
      if (!playerNames.containsKey(entry.getKey())) {
        // 如果找不到玩家名称，尝试从服务器获取
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(entry.getKey());
        if (serverPlayer != null) {
          recordPlayerName(serverPlayer);
        } else {
          // 如果无法获取玩家，使用UUID作为名称
          recordPlayerName(entry.getKey(), "Unknown(" + entry.getKey().toString().substring(0, 8) + ")");
        }
      }
    }
    currentReplayData.setPlayerRoles(roleMap);
  }

  public void updateRolesFromComponent(io.wifi.starrailexpress.cca.SREGameWorldComponent component) {
    currentReplayData
        .setCivilianPlayers(component.getAllWithRole(io.wifi.starrailexpress.api.TMMRoles.CIVILIAN));
    currentReplayData
        .setKillerPlayers(component.getAllWithRole(io.wifi.starrailexpress.api.TMMRoles.KILLER));
    currentReplayData
        .setVigilantePlayers(component.getAllWithRole(io.wifi.starrailexpress.api.TMMRoles.VIGILANTE));
    currentReplayData
        .setLooseEndPlayers(component.getAllWithRole(io.wifi.starrailexpress.api.TMMRoles.LOOSE_END));

    Map<UUID, String> roleMap = new HashMap<>();
    for (Map.Entry<UUID, io.wifi.starrailexpress.api.SRERole> entry : component.getRoles().entrySet()) {
      roleMap.put(entry.getKey(), entry.getValue().identifier().toString());
    }
    currentReplayData.setPlayerRoles(roleMap);
  }

  public void finalizeReplay(GameUtils.WinStatus winStatus, SREGameRoundEndComponent roundEndData) {
    currentReplayData.setWinningTitle(null);
    if (winStatus.equals(WinStatus.CUSTOM)) {
      currentReplayData.setWinningTeam(roundEndData.CustomWinnerID);
    } else if (winStatus.equals(WinStatus.CUSTOM_COMPONENT)) {
      currentReplayData.setWinningTeam(roundEndData.CustomWinnerID);
      currentReplayData.setWinningTitle(roundEndData.CustomWinnerTitle);
    } else {
      currentReplayData.setWinningTeam(winStatus.name());
    }

    saveReplay();
  }

  public void recordPlayerName(Player player) {
    playerNames.put(player.getUUID(), player.getName().getString());
  }

  public void recordPlayerName(UUID uuid, String name) {
    playerNames.put(uuid, name);
  }

  public void recordPlayerNames(Map<UUID, String> playerNamesMap) {
    playerNames.putAll(playerNamesMap);
  }

  public boolean isPlayerNameRecorded(UUID uuid) {
    return playerNames.containsKey(uuid);
  }

  public Map<UUID, String> getPlayerNames() {
    return new HashMap<>(playerNames);
  }

  public Component getPlayerName(UUID uuid) {
    String name = playerNames.get(uuid);
    if (name != null) {
      return Component.nullToEmpty(name);
    } else {
      // 如果在回放期间遇到未记录的玩家，尝试从服务器获取名称
      if (server != null) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);
        if (serverPlayer != null) {
          String playerName = serverPlayer.getName().getString();
          recordPlayerName(uuid, playerName); // 记录以便将来使用
          return Component.nullToEmpty(playerName);
        }
      }
      // 如果无法获取玩家名称，返回带UUID的描述
      return Component.nullToEmpty("未知玩家(" + uuid.toString().substring(0, 8) + ")");
    }
  }

  public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
      String message) {
    return addEvent(type, sourcePlayer, targetPlayer, itemUsed, message, null);
  }

  public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
      String message, HolderLookup.Provider provider) {
    return addEvent(type, sourcePlayer, targetPlayer, itemUsed, message, provider, false);
  }

  public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
      String message, boolean hidden) {
    return addEvent(type, sourcePlayer, targetPlayer, itemUsed, message, null, hidden);
  }

  public Component addEvent(GameReplayData.EventType type, UUID sourcePlayer, UUID targetPlayer, String itemUsed,
      String message, HolderLookup.Provider provider, boolean hidden) {
    SREGameWorldComponent sreGameWorldComponent = SREGameWorldComponent.KEY.get(SRE.SERVER.getLevel(Level.OVERWORLD));

    // 对可能为null的字符串参数进行处理
    String safeItemUsed = itemUsed != null ? itemUsed : "minecraft:air";
    String safeMessage = message != null ? message : "";
    GameReplayData.ReplayEvent event = new GameReplayData.ReplayEvent(type, sourcePlayer, targetPlayer,
        safeItemUsed, safeMessage, hidden);
    currentReplayData
        .addEvent(event);
    ReplayEvent event1 = convertReplayEvent(event, provider);
    Component eventText = null;
    boolean timelineRecorded = false;
    try {
      eventText = currentReplayData.toText(this, currentReplayData, event1);
      recordTimelineEvent(event, event1, eventText);
      timelineRecorded = true;
      if (eventText != null && !hidden) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(SRE.SERVER.overworld());
        if (SREConfig.instance().logGameEvent) {
          SRE.LOGGER.info("[GAME REPLAY] " + Component
              .translatable("%s", eventText)
              .withStyle(ChatFormatting.WHITE).getString());
        }
        Component finalEventText = eventText;
        SRE.SERVER.getPlayerList().getPlayers().forEach(
            player -> {
              if (gameWorldComponent != null && gameWorldComponent.isRunning()
                  && (!GameUtils.isPlayerAliveAndSurvival(player) || SRE.canSendReplay.stream().anyMatch((pre) -> {
                      return pre.test(player);
                    }))) {
                try {

                  {
                    var cantSend = SRE.cantSendReplay.stream().anyMatch((pre) -> {
                      return pre.test(player);
                    });
                    if (!cantSend) {
                      sendSystemMessage(player, Component
                          .translatable("%s %s",
                              Component.translatable("sre.replay.event").withStyle(ChatFormatting.GOLD), finalEventText)
                          .withStyle(ChatFormatting.WHITE));
                    }
                  }
                } catch (Exception e) {

                }
              }
            });
      }

      return eventText;
    } catch (Exception ignored) {
      if (!timelineRecorded) {
        recordTimelineEvent(event, event1, eventText);
      }
    }
    return null;
  }

  private void recordTimelineEvent(GameReplayData.ReplayEvent dataEvent, ReplayEvent replayEvent, Component eventText) {
    if (dataEvent == null || replayEvent == null || recorder == null || session == null) {
      return;
    }
    recorder.record(buildTimelineEvent(dataEvent, replayEvent, eventText));
  }

  private ReplayTimelineEvent buildTimelineEvent(GameReplayData.ReplayEvent dataEvent, ReplayEvent replayEvent,
      Component eventText) {
    Component text = eventText != null ? eventText
        : Component.literal(dataEvent.getType().name()).withStyle(ChatFormatting.DARK_GRAY);
    long startTime = session.startTimestamp();
    return new ReplayTimelineEvent(
        UUID.randomUUID(),
        replayEvent.eventType(),
        dataEvent.getTimestamp(),
        startTime <= 0L ? 0L : Math.max(0L, dataEvent.getTimestamp() - startTime),
        dataEvent.getSourcePlayer() == null ? null : session.profile(dataEvent.getSourcePlayer()),
        dataEvent.getTargetPlayer() == null ? null : session.profile(dataEvent.getTargetPlayer()),
        text,
        dataEvent.isHidden(),
        session.eventData(dataEvent));
  }

  // public static List<Predicate<Player>> cantSeeEvent = new ArrayList<>();
  public Component recordCustomEvent(Component msg) {
    return recordCustomEvent(msg, false);
  }

  public Component recordCustomEvent(Component msg, boolean hidden) {
    if (SRE.SERVER != null) {
      var provider = SRE.SERVER.registryAccess();
      String msgStr = Component.Serializer.toJson(msg, provider);
      return addEvent(GameReplayData.EventType.CUSTOM_MESSAGE, null, null, null, msgStr, provider, hidden);
    }
    return Component.literal("Error: unable to get SRE.SERVER").withStyle(ChatFormatting.RED);
  }

  public Component recordPlayerKill(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason) {
    return recordPlayerKill(killerUuid, victimUuid, deathReason, false);
  }

  public Component recordPlayerKill(UUID killerUuid, UUID victimUuid, ResourceLocation deathReason, boolean hidden) {
    String deathReasonStr = deathReason != null ? deathReason.toString() : "unknown";
    return addEvent(GameReplayData.EventType.PLAYER_KILL, killerUuid, victimUuid, deathReasonStr, null, hidden);
  }

  /**
   * @param player
   * @param role   可为空，为空默认为当前玩家职业。
   */
  public void recordPlayerRevival(UUID player, @Nullable SRERole role) {
    recordPlayerRevival(player, role, false);
  }

  public void recordPlayerRevival(UUID player, @Nullable SRERole role, boolean hidden) {
    String rolen = "";
    SRERole trole = role;
    if (trole == null) {
      trole = SREGameWorldComponent.KEY.get(SRE.SERVER.overworld()).getRole(player);
      if (trole == null) {
        trole = TMMRoles.CIVILIAN;
      }
    }
    if (trole != null)
      rolen = trole.identifier().getPath();
    addEvent(GameReplayData.EventType.PLAYER_REVIVAL, player, null, "", rolen, hidden);
  }

  public void recordPlayerRoleChange(UUID player, SRERole oldRole, SRERole newRole) {
    recordPlayerRoleChange(player, oldRole, newRole, false);
  }

  public void recordPlayerRoleChange(UUID player, SRERole oldRole, SRERole newRole, boolean hidden) {
    String old_role_str = "unknown";
    String new_role_str = "unknown";
    if (oldRole != null)
      old_role_str = oldRole.identifier().getPath();
    if (newRole != null)
      new_role_str = newRole.identifier().getPath();
    addEvent(GameReplayData.EventType.CHANGE_ROLE, player, null, "", old_role_str + "===" + new_role_str, hidden);
  }

  public void recordStoreBuy(UUID playerUuid, ResourceLocation itemBought, int amount, int price) {
    recordStoreBuy(playerUuid, itemBought, amount, price, false);
  }

  public void recordStoreBuy(UUID playerUuid, ResourceLocation itemBought, int amount, int price, boolean hidden) {
    String itemBoughtStr = itemBought != null ? itemBought.toString() : "unknown";
    addEvent(GameReplayData.EventType.STORE_BUY, playerUuid, null, itemBoughtStr + ":" + amount,
        String.valueOf(price), hidden);
  }

  public void recordItemUse(UUID playerUuid, ResourceLocation itemUsed) {
    recordItemUse(playerUuid, itemUsed, false);
  }

  public void recordItemUse(UUID playerUuid, ResourceLocation itemUsed, boolean hidden) {
    String itemUsedStr = itemUsed != null ? itemUsed.toString() : "unknown";
    addEvent(GameReplayData.EventType.ITEM_USED, playerUuid, null, itemUsedStr, null, hidden);
  }

  public void breakArmor(UUID playerUuid) {
    addEvent(GameReplayData.EventType.ARMOR_BREAK, playerUuid, null, "unknown", null, false);
  }

  public void recordSkillUsed(UUID playerUuid, ResourceLocation skillUsed) {
    recordSkillUsed(playerUuid, skillUsed, false);
  }

  public void recordSkillUsed(UUID playerUuid, ResourceLocation skillUsed, boolean hidden) {
    String skillUsedStr = skillUsed != null ? skillUsed.toString() : "unknown";
    addEvent(GameReplayData.EventType.SKILL_USED, playerUuid, null, skillUsedStr, null, hidden);
  }

  public void setPlayerCount(int count) {
    currentReplayData.setPlayerCount(count);
  }

  public void setCivilianPlayers(java.util.List<UUID> players) {
    currentReplayData.setCivilianPlayers(players);
  }

  public void setKillerPlayers(java.util.List<UUID> players) {
    currentReplayData.setKillerPlayers(players);
  }

  public void setVigilantePlayers(java.util.List<UUID> players) {
    currentReplayData.setVigilantePlayers(players);
  }

  public void setLooseEndPlayers(java.util.List<UUID> players) {
    currentReplayData.setLooseEndPlayers(players);
  }

  public void setWinningPlayer(UUID player) {
    currentReplayData.setWinningPlayer(player);
  }

  public void setWinningTeam(String team) {
    currentReplayData.setWinningTeam(team);
  }

  public GameReplay getCurrentReplay() {
    return currentReplay;
  }

  public ReplaySession getSession() {
    return session;
  }

  public List<ReplayTimelineEvent> getTimelineEvents(boolean includeHidden) {
    if (session == null) {
      return List.of();
    }
    List<ReplayTimelineEvent> events = session.timelineSnapshot();
    if (events.isEmpty() && currentReplayData != null && !currentReplayData.getTimeline().isEmpty()) {
      events = currentReplayData.getTimeline().stream()
          .map(event -> {
            ReplayEvent replayEvent = convertReplayEvent(event, SRE.SERVER == null ? null : SRE.SERVER.registryAccess());
            Component text = null;
            try {
              text = currentReplayData.toText(this, currentReplayData, replayEvent);
            } catch (Exception ignored) {
            }
            return buildTimelineEvent(event, replayEvent, text);
          })
          .toList();
    }
    if (includeHidden) {
      return events;
    }
    return events.stream().filter(event -> !event.hidden()).toList();
  }

  public Component generateScreenReplay(int maxLines) {
    return formatter.formatScreen(currentReplayData, getTimelineEvents(true), maxLines);
  }

  public List<Component> generateScreenReplayLines(int maxLines) {
    return formatter.formatScreenLines(currentReplayData, getTimelineEvents(true), maxLines);
  }

  @Override
  public void recordEvent(ReplayEventTypes.EventType eventType, ReplayEventTypes.EventDetails details) {
    recordEvent(eventType, details, false);
  }

  public void recordEvent(ReplayEventTypes.EventType eventType, ReplayEventTypes.EventDetails details, boolean hidden) {
    if (eventType == null) {
      return;
    }
    if (details instanceof ReplayEventTypes.PlayerKillDetails kill) {
      recordPlayerKill(kill.killerUuid(), kill.victimUuid(), kill.deathReason(), hidden);
    } else if (details instanceof ReplayEventTypes.PlayerRevivalDetails revival) {
      addEvent(GameReplayData.EventType.PLAYER_REVIVAL, revival.player(), null, "", revival.role(), hidden);
    } else if (details instanceof ReplayEventTypes.ChangeRoleDetails role) {
      addEvent(GameReplayData.EventType.CHANGE_ROLE, role.player(), null, "",
          role.oldRole() + "===" + role.newRole(), hidden);
    } else if (details instanceof ReplayEventTypes.ItemUsedDetails item) {
      addEvent(GameReplayData.EventType.ITEM_USED, item.playerUuid(), null, item.itemId().toString(), null, hidden);
    } else if (details instanceof ReplayEventTypes.StoreBuyDetails store) {
      addEvent(GameReplayData.EventType.STORE_BUY, store.playerUuid(), null,
          store.itemId() + ":1", String.valueOf(store.cost()), hidden);
    } else if (details instanceof ReplayEventTypes.ArmorBreakDetails armor) {
      addEvent(GameReplayData.EventType.ARMOR_BREAK, armor.playerUuid(), null, "unknown", null, hidden);
    } else if (details instanceof ReplayEventTypes.CustomEventDetails custom) {
      recordCustomEvent(custom.Message(), hidden);
    } else {
      addEvent(toLegacyEventType(eventType), null, null, null, null, hidden);
    }
  }

  @Override
  public void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message) {
    Component component = Component.literal("[" + customEventTypeId + "] " + (message == null ? "" : message));
    addEvent(GameReplayData.EventType.CUSTOM_MESSAGE, playerUuid, null, null,
        SRE.SERVER == null ? component.getString() : Component.Serializer.toJson(component, SRE.SERVER.registryAccess()),
        SRE.SERVER == null ? null : SRE.SERVER.registryAccess(), false);
  }

  public void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message, boolean hidden) {
    Component component = Component.literal("[" + customEventTypeId + "] " + (message == null ? "" : message));
    addEvent(GameReplayData.EventType.CUSTOM_MESSAGE, playerUuid, null, null,
        SRE.SERVER == null ? component.getString() : Component.Serializer.toJson(component, SRE.SERVER.registryAccess()),
        SRE.SERVER == null ? null : SRE.SERVER.registryAccess(), hidden);
  }

  private GameReplayData.EventType toLegacyEventType(ReplayEventTypes.EventType eventType) {
    return switch (eventType) {
      case GAME_START -> GameReplayData.EventType.GAME_START;
      case GAME_END -> GameReplayData.EventType.GAME_END;
      case PLAYER_JOIN -> GameReplayData.EventType.PLAYER_JOIN;
      case PLAYER_LEAVE -> GameReplayData.EventType.PLAYER_LEAVE;
      case PLAYER_KILL -> GameReplayData.EventType.PLAYER_KILL;
      case PLAYER_POISONED -> GameReplayData.EventType.PLAYER_POISONED;
      case TASK_COMPLETE -> GameReplayData.EventType.TASK_COMPLETE;
      case STORE_BUY -> GameReplayData.EventType.STORE_BUY;
      case DOOR_LOCK -> GameReplayData.EventType.DOOR_LOCK;
      case DOOR_UNLOCK -> GameReplayData.EventType.DOOR_UNLOCK;
      case DOOR_OPEN -> GameReplayData.EventType.DOOR_OPEN;
      case DOOR_CLOSE -> GameReplayData.EventType.DOOR_CLOSE;
      case LOCKPICK_ATTEMPT -> GameReplayData.EventType.LOCKPICK_ATTEMPT;
      case ITEM_USED -> GameReplayData.EventType.ITEM_USED;
      case MOOD_CHANGE -> GameReplayData.EventType.MOOD_CHANGE;
      case PSYCHO_STATE_CHANGE -> GameReplayData.EventType.PSYCHO_STATE_CHANGE;
      case BLACKOUT_START -> GameReplayData.EventType.BLACKOUT_START;
      case BLACKOUT_END -> GameReplayData.EventType.BLACKOUT_END;
      case GRENADE_THROWN -> GameReplayData.EventType.GRENADE_THROWN;
      case CHANGE_ROLE -> GameReplayData.EventType.CHANGE_ROLE;
      case PLAYER_REVIVAL -> GameReplayData.EventType.PLAYER_REVIVAL;
      case ARMOR_BREAK -> GameReplayData.EventType.ARMOR_BREAK;
      case CUSTOM_EVENT -> GameReplayData.EventType.CUSTOM_MESSAGE;
    };
  }

  public List<ReplayEvent> getEvents() {
    return currentReplayData.getTimeline().stream()
        .map(event -> convertReplayEvent(event, SRE.SERVER == null ? null : SRE.SERVER.registryAccess()))
        .toList();
  }

  public List<ReplayEvent> getEventsInTimeRange(long startTime, long endTime) {
    return getEvents().stream()
        .filter(event -> event.timestamp() >= startTime && event.timestamp() <= endTime)
        .toList();
  }

  public List<ReplayEvent> getEventsByPlayer(UUID playerUuid) {
    return currentReplayData.getTimeline().stream()
        .filter(event -> playerUuid != null
            && (playerUuid.equals(event.getSourcePlayer()) || playerUuid.equals(event.getTargetPlayer())))
        .map(event -> convertReplayEvent(event, SRE.SERVER == null ? null : SRE.SERVER.registryAccess()))
        .toList();
  }

  public List<ReplayEvent> getEventsByType(ReplayEventTypes.EventType eventType) {
    return getEvents().stream().filter(event -> event.eventType() == eventType).toList();
  }

  public List<UUID> getAllPlayerUuids() {
    return currentReplayData.getPlayerRoles().keySet().stream().toList();
  }

  public Optional<String> getPlayerNameOptional(UUID playerUuid) {
    return Optional.ofNullable(playerNames.get(playerUuid));
  }

  public void saveReplay() {
    try {
      storage.save(currentReplayData, getTimelineEvents(true));
    } catch (IOException e) {
      SRE.LOGGER.error("Failed to save game replay", e);
    }
  }

  public GameReplayData loadReplay() {
    File replayFile = new File(getInstance().getGameDir().toFile(), REPLAY_FILE_NAME);
    if (!replayFile.exists()) {
      SRE.LOGGER.warn("No previous game replay found.");
      return null;
    }
    try (FileReader reader = new FileReader(replayFile)) {
      try {
        GameReplayData loadedData = GSON.fromJson(reader, GameReplayData.class);
        SRE.LOGGER.info("Game replay loaded from {}", replayFile.getAbsolutePath());
        return loadedData;
      } catch (Exception e) {
        e.printStackTrace();
      }
      return new GameReplayData();
    } catch (IOException e) {
      SRE.LOGGER.error("Failed to load game replay", e);
      return null;
    }
  }

  public static void sendSystemMessage(ServerPlayer player, Component message) {
    if (player != null && message != null) {
      try {
        player.sendSystemMessage(message);
      } catch (Exception e) {
        SRE.LOGGER.error("Error sending system message: ", e);
      }
    }
  }

  public Component generateReplay() {
    return generateReplay(false);
  }

  public Component generateReplay(boolean includeHidden) {
    GameReplayData replayData = currentReplayData;
    if (replayData == null) {
      replayData = loadReplay();
    }
    return generateReplayFromData(replayData, includeHidden);
  }

  Component generateReplayFromData(GameReplayData replayData, boolean includeHidden) {
    if (replayData == null) {
      return Component.translatable("sre.replay.error.no_data").withStyle(ChatFormatting.RED);
    }
    // Clear previous messages
    MutableComponent text = Component.literal("\n".repeat(50));
    text.append(Component.translatable("sre.replay.header").withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD))
        .append("\n");
    Integer playerCount = replayData.getPlayerCount();
    text.append(Component.translatable("sre.replay.player_count", playerCount != null ? playerCount : 0)
        .withStyle(ChatFormatting.WHITE)).append("\n");

    text.append(Component.literal("---").withStyle(ChatFormatting.GRAY)).append("\n");

    Map<UUID, String> playerRoles = replayData.getPlayerRoles();
    if (playerRoles != null && !playerRoles.isEmpty()) {
      List<UUID> deadPlayers = getDeadPlayers(replayData);

      // 分别获取不同阵营的存活和死亡玩家
      List<UUID> aliveCivilians = new java.util.ArrayList<>();
      List<UUID> deadCivilians = new java.util.ArrayList<>();
      List<UUID> aliveNeutrals = new java.util.ArrayList<>();
      List<UUID> deadNeutrals = new java.util.ArrayList<>();
      List<UUID> aliveKillers = new java.util.ArrayList<>();
      List<UUID> deadKillers = new java.util.ArrayList<>();

      for (Map.Entry<UUID, String> entry : playerRoles.entrySet()) {
        UUID uuid = entry.getKey();
        String roleId = entry.getValue();
        if (roleId == null)
          continue; // 跳过空的角色ID
        boolean isDead = deadPlayers.contains(uuid);
        final var first = TMMRoles.ROLES.values().stream()
            .filter(role -> role.identifier().toString().equals(roleId)).findFirst();
        // 根据角色ID分类
        if (first.isPresent() && first.get().isInnocent()) {
          if (isDead) {
            deadCivilians.add(uuid);
          } else {
            aliveCivilians.add(uuid);
          }
        } else {

          if (first.isPresent() && first.get().canUseKiller()) {
            if (isDead) {
              deadKillers.add(uuid);
            } else {
              aliveKillers.add(uuid);
            }
          } else {
            if (first.isPresent() && !first.get().isInnocent()) {
              // 其他角色归类为中立
              if (isDead) {
                deadNeutrals.add(uuid);
              } else {
                aliveNeutrals.add(uuid);
              }
            }

          }
        }
      }

      // 显示平民
      if (!aliveCivilians.isEmpty() || !deadCivilians.isEmpty()) {
        text.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(
            Component.translatable("sre.replay.civilians").withStyle(ChatFormatting.BLUE)).append("\n");
        if (!aliveCivilians.isEmpty()) {
          MutableComponent aliveCivText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              aliveCivilians, playerRoles, "", replayData, true);
          if (aliveCivText != null) {
            text.append(aliveCivText).append("\n");
          }
        }
        if (!deadCivilians.isEmpty()) {
          MutableComponent deadCivText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              deadCivilians, playerRoles, "", replayData, false);
          if (deadCivText != null) {
            text.append(deadCivText).append("\n");
          }
        }
      }

      // 显示中立
      if (!aliveNeutrals.isEmpty() || !deadNeutrals.isEmpty()) {
        text.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(
            Component.translatable("sre.replay.neutrals").withStyle(ChatFormatting.YELLOW)).append("\n");
        if (!aliveNeutrals.isEmpty()) {
          MutableComponent aliveNeutText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              aliveNeutrals, playerRoles, "", replayData, true);
          if (aliveNeutText != null) {
            text.append(aliveNeutText).append("\n");
          }
        }
        if (!deadNeutrals.isEmpty()) {
          MutableComponent deadNeutText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              deadNeutrals, playerRoles, "", replayData, false);
          if (deadNeutText != null) {
            text.append(deadNeutText).append("\n");
          }
        }
      }
      // 显示杀手
      if (!aliveKillers.isEmpty() || !deadKillers.isEmpty()) {
        text.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(
            Component.translatable("sre.replay.killers").withStyle(ChatFormatting.DARK_RED)).append("\n");
        if (!aliveKillers.isEmpty()) {
          MutableComponent aliveKillText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              aliveKillers, playerRoles, "", replayData, true);
          if (aliveKillText != null) {
            text.append(aliveKillText).append("\n");
          }
        }
        if (!deadKillers.isEmpty()) {
          MutableComponent deadKillText = ReplayDisplayUtils.buildTeamPlayerRolesWithDeathStatus(this,
              deadKillers, playerRoles, "", replayData, false);
          if (deadKillText != null) {
            text.append(deadKillText).append("\n");
          }
        }
      }
    }
    text.append(Component.literal("---").withStyle(ChatFormatting.GRAY)).append("\n");

    // Send winning information
    String winningTeam = replayData.getWinningTeam();
    if (winningTeam != null) {
      text.append(
          Component
              .translatable("sre.replay.winning_team",
                  replayData.getWinningTitle()
                      .withStyle(ChatFormatting.GOLD))
              .withStyle(ChatFormatting.WHITE))
          .append("\n");
    }

    text.append(Component.literal("---").withStyle(ChatFormatting.GRAY)).append("\n");

    // Send timeline
    text.append(
        Component.translatable("sre.replay.timeline").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE))
        .append("\n");

    long gameStartTime = ReplayDisplayUtils.findGameStartTime(replayData);
    List<GameReplayData.ReplayEvent> timeline = replayData.getTimeline();
    if (timeline != null) {
      for (GameReplayData.ReplayEvent dataEvent : timeline) {
        if (dataEvent == null)
          continue; // 跳过空事件
        if (dataEvent.isHidden() && !includeHidden)
          continue;
        long relativeTime = dataEvent.getTimestamp() - gameStartTime;
        String timePrefix = ReplayDisplayUtils.formatTime(relativeTime) + " ";
        ReplayEvent event = null;
        if (SRE.SERVER != null) {
          event = convertReplayEvent(dataEvent, SRE.SERVER.registryAccess());
        } else {
          event = convertReplayEvent(dataEvent, null);
        }
        Component eventText = null;
        try {
          eventText = replayData.toText(this, replayData, event);
        } catch (Exception e) {
          SRE.LOGGER.error("Error converting replay event to text: ", e);
        }
        if (eventText != null) {
          text.append(Component.literal(timePrefix).append(eventText)).append("\n");
        } else {
        }
      }
    }

    text.append(Component.literal("---").withStyle(ChatFormatting.GRAY)).append("\n");
    text.append(Component.translatable("sre.replay.footer").withStyle(ChatFormatting.GRAY));
    return text;
  }

  private List<UUID> getDeadPlayers(GameReplayData replayData) {
    List<UUID> dead = new java.util.ArrayList<>();
    for (GameReplayData.ReplayEvent event : replayData.getTimeline()) {
      if (event.getType() == GameReplayData.EventType.PLAYER_KILL) {
        UUID target = event.getTargetPlayer();
        if (target != null && !dead.contains(target)) {
          dead.add(target);
        }
      }
    }
    return dead;
  }

  public void recordItemEatFlaggedItem(Player player, Item item, String flagName) {
    recordCustomEvent(
        Component.translatable("replay.event.drink." + flagName,
            GameReplayUtils.getReplayPlayerDisplayText(player, true),
            GameReplayUtils.getItemDisplayName(BuiltInRegistries.ITEM.getKey(item))));
  }

  public Component recordPlayerNotKilled(Player killer, Player victim, ResourceLocation deathReason) {
    if (killer == null) {
      return SRE.REPLAY_MANAGER.recordCustomEvent(
          Component.translatable("replay.event.game.failed_death",
              GameReplayUtils.getReplayPlayerDisplayText(victim, true)));
    }
    return SRE.REPLAY_MANAGER.recordCustomEvent(
        Component.translatable("replay.event.game.failed_kill",
            GameReplayUtils.getReplayPlayerDisplayText(victim, true),
            GameReplayUtils.getReplayPlayerDisplayText(killer, true)));
  }
}
