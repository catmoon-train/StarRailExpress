/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.agmas.harpymodloader.commands.ListRolesCommand;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.item.RadioItem;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.NameTagSyncPayload;
import org.agmas.noellesroles.utils.LocalDateData;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.event.AllowGameInitialize;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.command.AutoShutdownWhenNotRunningCommand;
import io.wifi.starrailexpress.content.command.ListRoleInRoundCommand;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameInitialized;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.event.OnTrainAreaHaveReseted;
import io.wifi.starrailexpress.game.modes.funny.SRERandomGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.MapDepartCancelPayload;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import static io.wifi.starrailexpress.game.GameUtils.*;

/** 对局准备、开局和收尾；公开兼容入口与共享状态仍由 GameUtils 持有。 */
final class GameLifecycle {
    private static Set<UUID> forcedReadyPlayers;

    private GameLifecycle() {
    }

    public static void startGame(ServerLevel world, GameMode gameMode, int time) {
        if (SRE.isLobby || isStartingGame)
            return;
        if (SREGameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        // 随机模式只负责抽取：在写入 gameMode、创建重置任务和触发开局事件之前，
        // 整体替换成抽中的真实模式，后续流程（含 HUD、存档、模式专属逻辑）都按真实模式运行。
        GameMode resolvedMode = SRERandomGameMode.resolve(gameMode,
                AreasWorldComponent.KEY.get(world).gameModes, getStartingPlayers(world).size());
        if (resolvedMode != gameMode) {
            SRE.LOGGER.info("随机模式 {} 抽中 {}", gameMode.identifier, resolvedMode.identifier);
            world.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("sre.random_mode.selected", gameMode.getName(), resolvedMode.getName())
                            .withStyle(ChatFormatting.GREEN),
                    false);
            gameMode = resolvedMode;
        }
        if (io.wifi.starrailexpress.settlement.SettlementManager.hasViewers()
                && (forcedReadyPlayers == null || forcedReadyPlayers.isEmpty())) {
            setForcedReadyPlayers(getStartingPlayers(world).stream().map(ServerPlayer::getUUID).toList());
        }
        io.wifi.starrailexpress.settlement.SettlementManager.stopAll(world.getServer());
        if (!AllowGameInitialize.EVENT.invoker().allow(world, gameMode, List.copyOf(getStartingPlayers(world)))) {
            clearForcedReadyPlayers();
            return;
        }
        isStartingGame = true;
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        if (areas.mapName == null) {
            MapManager.loadRandomMap(world);
        } else {
            // 重新加载当前地图配置，确保获取最新配置
            MapManager.loadMap(world, areas.mapName);
        }
        MapResetManager.loadArea(world);
        if (areas.areasSettings.noReset) {
            resetEntities(world);
            SRE.LOGGER.info("NO RESET MAP!");
            trueStartGame(world, gameMode, time);
            return;
        }
        if (areas.areasSettings.mustCopy || resetPoints.isEmpty() || SREConfig.instance().enableAutoTrainReset) {
            var task = new ServerTaskInfoClasses.FullTrainResetTask(areas, world, gameMode, time);
            serverTaskQueue.add(task);
        } else {
            var task = new ServerTaskInfoClasses.OnlySomeBlockResetTask(resetPoints, world, gameMode, time, areas);
            serverTaskQueue.add(task);
        }
    }


    public static void trueStartGame(ServerLevel world, GameMode gameMode, int time) {
        if (SRE.isLobby)
            return;
        // 延迟5s
        SRE.LOGGER.info("=".repeat(20));
        SRE.LOGGER.info("Game Started!");
        executeFunction(world.getServer().createCommandSourceStack(), "harpymodloader:early_start_game");
        executeFunction(world.getServer().createCommandSourceStack(),
                "harpymodloader:early_start_game_" + MapManager.last_start_map);
        List<ServerPlayer> players = world.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, new CloseUiPayload());
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        int playerCount = getStartingPlayers(world).size();
        game.gameMode = (gameMode);
        SREGameTimeComponent.KEY.get(world).setResetTime(time);
        RefugeeComponent.KEY.get(world).reset();
        if (playerCount >= gameMode.minPlayerCount) {
            game.setGameStatus(SREGameWorldComponent.GameStatus.STARTING);
        } else {
            clearForcedReadyPlayers();
            for (ServerPlayer player : players) {
                player.displayClientMessage(
                        Component.translatable("game.start_error.sre.not_enough_players", gameMode.minPlayerCount),
                        true);
                // 无法发车：让客户端关闭地图投票结果页的铺黑，避免卡黑屏
                ServerPlayNetworking.send(player, new MapDepartCancelPayload());
            }
            isStartingGame = false;
        }
    }

    public static void stopGame(ServerLevel world) {
        // 游戏结束流程一启动就立即撤销远距离监控摄像头的强加载票据，不等结算/回放阶段跑完。
        SecurityCameraChunkTickets.release(world);
        world.players().forEach(serverPlayer -> {
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.INVINCIBLE, 80, 0, false, false, false));
            serverPlayer.addEffect(new MobEffectInstance(ModEffects.USED_BANED, 80, 0, false, false, false));
        });
        SREGameWorldComponent component = SREGameWorldComponent.KEY.get(world);
        SREWorldBlackoutComponent.KEY.get(world).reset();
        component.setGameStatus(SREGameWorldComponent.GameStatus.STOPPING);
        component.gameMode.stopGame(world);
        org.agmas.noellesroles.game.MirrorReunionEndEgg.onGameStopping(world, component);
    }


    public static void initializeGame(ServerLevel serverWorld) {
        io.wifi.starrailexpress.event.GameUtilityEvents.BEFORE_INITIALIZE.invoker().beforeInitialize(serverWorld);
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
        io.wifi.starrailexpress.settlement.SettlementManager.stopAll(serverWorld.getServer());
        ArrayList<ServerPlayer> readyPlayerList = new ArrayList<>(getStartingPlayers(serverWorld));
        if (io.wifi.starrailexpress.event.DeferGameInitialize.EVENT.invoker()
                .defer(serverWorld, gameComponent.getGameMode(), List.copyOf(readyPlayerList))) return;
        if (!AllowGameInitialize.EVENT.invoker().allow(serverWorld, gameComponent.getGameMode(),
                List.copyOf(readyPlayerList))) {
            clearForcedReadyPlayers();
            isStartingGame = false;
            isGameStarted = false;
            gameComponent.setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
            gameComponent.setFade(0);
            gameComponent.sync();
            for (ServerPlayer player : serverWorld.players()) {
                ServerPlayNetworking.send(player, new MapDepartCancelPayload());
            }
            return;
        }
        OnGameInitialized.EVENT.invoker().onGameInitialized(serverWorld);
        isGameStarted = false;
        SRERole.resetStatic();
        var packet = ListRolesCommand.getRoleAndModifierEnableInfoPacket(false);
        for (var p : serverWorld.players()) {
            ServerPlayNetworking.send(p, packet);
        }
        isStartingGame = false;
        HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
        RadioItem.RADIO_GROUP.clear();
        gameComponent.clear();
        gameComponent.isSkillAvailable = true;
        // AreasWorldComponent areasWorldComponent =
        // AreasWorldComponent.KEY.get(serverWorld);

        RoleMethodDispatcher.onStartGame(serverWorld);
        // 记录开局玩家数量，供基于开局人数的逻辑使用
        gameComponent.setStartingPlayerCount(readyPlayerList.size());
        clearForcedReadyPlayers();
        // serverWorld.setWeatherParameters(0, -1, true, true);
        List<ServerPlayer> players = new ArrayList<>(serverWorld.getServer().getPlayerList().getPlayers());
        // 在分配角色前将所有玩家设置为冒险模式，并且resetPlayer
        for (ServerPlayer player : players) {
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        }
        for (ServerPlayer player : readyPlayerList) {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        }
        serverCacheKillState.clear();

        GameInitializeEvent.EVENT.invoker().initializeGame(serverWorld, gameComponent, readyPlayerList);
        // 初始化房间等
        gameComponent.getGameMode().beforeInitializeGame(serverWorld, gameComponent, readyPlayerList);
        // 分配角色
        gameComponent.getGameMode().initializeGame(serverWorld, gameComponent, readyPlayerList);
        // 初始化回放管理器
        gameComponent.getGameMode().afterInitializeGame(serverWorld, gameComponent, readyPlayerList);

        // 准备游戏开始（过渡状态）
        gameComponent.setGameStatus(SREGameWorldComponent.GameStatus.ACTIVE);

        gameComponent.sync();

        gameComponent.getGameMode().recordPlayerStats(serverWorld, gameComponent, readyPlayerList);

        gameComponent.getGameMode().gameStarted(serverWorld, gameComponent, readyPlayerList);

        // 发放地图初始物品
        distributeMapInitialItems(serverWorld, readyPlayerList);
        gameComponent.playerBannedBlockTime.clear();
        OnGameStarted.EVENT.invoker().onGameStarted(serverWorld);

        TimeStopEffect.canMovePlayers.clear();
        // --- 结束新增统计数据更新逻辑 ---
        OnTrainAreaHaveReseted.EVENT.invoker().onWorldHaveInited(serverWorld);
        isGameStarted = true;
    }

    /**
     * 分发地图初始物品给所有玩家
     * 格式：initialItems 列表中每个元素为 "itemId;count"
     */
    private static void distributeMapInitialItems(ServerLevel serverWorld, List<ServerPlayer> players) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        if (areas.areasSettings.initialItems.isEmpty()) {
            return;
        }
        for (String entry : areas.areasSettings.initialItems) {
            String[] parts = entry.split(";");
            if (parts.length < 2)
                continue;
            String itemId = parts[0];
            int count;
            try {
                count = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            if (count <= 0)
                continue;

            ResourceLocation itemLocation = ResourceLocation.tryParse(itemId);
            if (itemLocation == null)
                continue;
            Item item = BuiltInRegistries.ITEM.get(itemLocation);
            if (item == Items.AIR)
                continue;

            for (ServerPlayer player : players) {
                player.addItem(new ItemStack(item, count));
            }
            SRE.LOGGER.info(
                    "Distributed map initial item: " + itemId + " x" + count + " to " + players.size() + " players");
        }
    }

    /**
     * 基础重置，包含分配房间、重置玩家、游戏规则等
     *
     * @param serverWorld
     * @param gameComponent
     * @param players
     */
    public static void baseInitialize(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            List<ServerPlayer> players) {
        if (SRE.isLobby)
            return;
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        startTime = System.currentTimeMillis();

        SRETrainWorldComponent.KEY.get(serverWorld).reset();
        SREWorldBlackoutComponent.KEY.get(serverWorld).reset();
        // 重置画板已画出物品状态
        gameComponent.resetDrawnCategories();
        serverWorld.setDayTime(areas.areasSettings.time);
        serverWorld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, serverWorld.getServer());
        SREGameWorldComponent.perPlayerDarknessTime.clear();

        // 天气循环配置 - 默认关闭
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(areas.areasSettings.weatherCycle,
                serverWorld.getServer());

        // 应用地图天气配置
        switch (areas.areasSettings.weather) {
            case rain:
                serverWorld.setWeatherParameters(0, 120000, true, false);
                break;
            case thunder:
                serverWorld.setWeatherParameters(0, 120000, true, true);
                break;
            default: // clear
                serverWorld.setWeatherParameters(120000, 0, false, false);
                break;
        }

        // 昼夜循环配置 - 默认关闭，开启后使用正常昼夜循环
        serverWorld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(areas.areasSettings.daylightCycle,
                serverWorld.getServer());

        serverWorld.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE).set(9999,
                serverWorld.getServer());
        serverWorld.getServer().setDifficulty(Difficulty.PEACEFUL, true);

        // dismount all players as it can cause issues
        Map<UUID, String> nameTagMap = new HashMap<>();
        for (ServerPlayer player : serverWorld.players()) {
            NameTagInventoryComponent nameTagInventoryComponent = NameTagInventoryComponent.KEY.get(player);
            String effectiveNameTag = nameTagInventoryComponent.getEffectiveNameTag();
            if (effectiveNameTag != null && !effectiveNameTag.isEmpty()) {
                nameTagMap.put(player.getUUID(), effectiveNameTag);
            }
            player.removeVehicle();
            resetPlayer(player);
        }
        if (SREConfig.instance().isItemSkinEnabled) {
            NameTagSyncPayload payload = new NameTagSyncPayload(nameTagMap);
            for (ServerPlayer player : players) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        // teleport players to play area

        // teleport non playing players
        for (ServerPlayer player : serverWorld.getServer().getPlayerList().getPlayers()) {
            if (players.contains(player))
                continue;
            player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);

            AreasWorldComponent.PosWithOrientation spectatorSpawnPos = areas.getSpectatorSpawnPos();
            player.teleportTo(serverWorld, spectatorSpawnPos.pos.x(), spectatorSpawnPos.pos.y(),
                    spectatorSpawnPos.pos.z(), spectatorSpawnPos.yaw, spectatorSpawnPos.pitch);
        }

        // clear items, clear previous game data
        for (ServerPlayer serverPlayerEntity : players) {
            serverPlayerEntity.getInventory().clearContent();
            SREPlayerMoodComponent.KEY.get(serverPlayerEntity).init();
            SREPlayerShopComponent.KEY.get(serverPlayerEntity).init();
            SREPlayerPoisonComponent.KEY.get(serverPlayerEntity).init();
            SREPlayerPsychoComponent.KEY.get(serverPlayerEntity).init();
            SREPlayerNoteComponent.KEY.get(serverPlayerEntity).init();
            SREPlayerShopComponent.KEY.get(serverPlayerEntity).init();
            io.wifi.starrailexpress.cca.DynamicShopComponent.KEY.get(serverPlayerEntity).init();
            io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(serverPlayerEntity).init();
            if (!TrainVoicePlugin.isVoiceChatMissing()) {
                TrainVoicePlugin.resetPlayer(serverPlayerEntity.getUUID());
            }

            // remove item cooldowns
            HashSet<Item> copy = new HashSet<>(serverPlayerEntity.getCooldowns().cooldowns.keySet());
            for (Item item : copy)
                serverPlayerEntity.getCooldowns().removeCooldown(item);
        }
        gameComponent.clearRoleMap(true);
        SREGameTimeComponent.KEY.get(serverWorld).reset();

        // reset train 已经提前重置
        // gameComponent.queueTrainReset();

        // select rooms
        Collections.shuffle(players);
        int roomNumber = 0;
        int i = 0;
        int roomCount = areas.getRoomCount(); // Get room count from config
        // Echo 模式不使用房间钥匙与介绍信件：三格携带由模式自己发放。
        final boolean echoMode = gameComponent.getGameMode() != null
                && gameComponent.getGameMode().identifier.equals(org.agmas.noellesroles.game.modes.echo.EchoBootstrap.ID);
        for (ServerPlayer serverPlayerEntity : players) {
            ItemStack itemStack = new ItemStack(TMMItems.KEY);
            roomNumber = i % roomCount + 1;
            int finalRoomNumber = roomNumber;
            itemStack.update(DataComponents.LORE, ItemLore.EMPTY, component -> new ItemLore(Component
                    .nullToEmpty("Room " + finalRoomNumber)
                    .toFlatList(Style.EMPTY.withItalic(false).withColor(0xFF8C00))));
            if (!echoMode) serverPlayerEntity.addItem(itemStack);
            roomToPlayer.put(serverPlayerEntity.getUUID(), finalRoomNumber);

            // give letter
            ItemStack letter = new ItemStack(ModItems.LETTER_ITEM);
            {
                Component displayName = serverPlayerEntity.getName();
                letter.set(DataComponents.ITEM_NAME,
                        Component.translatable("tip.n.letter.item_name", displayName)
                                .withStyle(ChatFormatting.AQUA));

                int letterColor = 0xC5AE8B;
                String tipString = "tip.n.letter.";
                letter.update(DataComponents.LORE, ItemLore.EMPTY, component -> {
                    List<Component> text = new ArrayList<>();
                    UnaryOperator<Style> stylizer = style -> style.withItalic(false).withColor(letterColor);

                    String string = displayName != null ? displayName.getString()
                            : serverPlayerEntity.getName().getString();
                    if (string.charAt(string.length() - 1) == '\uE780') { // remove ratty supporter icon
                        string = string.substring(0, string.length() - 1);
                    }
                    text.add(Component
                            .translatable(tipString + "name", string,
                                    Component.translatable(tipString + "map_name"))
                            .withStyle(stylizer));
                    text.add(Component.translatable(tipString + "room").withStyle(stylizer));
                    var date = new LocalDateData();
                    text.add(Component.translatable(tipString + "tooltip1",
                            Component.translatable(tipString + "date", date.getYear(),
                                    date.getMonth(), date.getDay()))
                            .withStyle(stylizer));
                    text.add(Component.translatable(tipString + "tooltip2").withStyle(stylizer));
                    return new ItemLore(text);
                });
            }

            // 若存在赞助者，把信封替换为赞助者的 plush，但多给一个（可以丢弃）
            ItemStack sponsorPlush = io.wifi.starrailexpress.sponsor.SponsorManager.decorateIntroStack(letter,
                    serverPlayerEntity);
            if (!echoMode && sponsorPlush != null) {
                serverPlayerEntity.addItem(sponsorPlush);
            }

            if (!echoMode) serverPlayerEntity.addItem(letter);
            i++;
        }
        // 每次开局独立预留站位，不改变回房间、复活、旁观或局末回传的通用入口。
        GameStartSpawnScatter spawnScatter = SREConfig.instance().enableStartSpawnScatter
                ? new GameStartSpawnScatter() : null;
        boolean warnedAboutSpawn = false;
        for (ServerPlayer player : players) {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            Vec3 pos = getSpawnPos(areas, roomToPlayer.getOrDefault(player.getUUID(), 1));
            if (spawnScatter != null) {
                Vec3 originalSpawn = (pos != null ? pos : player.position().add(areas.getPlayAreaOffset()))
                        .add(0, 1, 0);
                Vec3 scattered = spawnScatter.find(player, originalSpawn, areas.getPlayArea());
                if (scattered != null) {
                    if (pos != null) {
                        player.teleportTo(scattered.x(), scattered.y(), scattered.z());
                    } else {
                        player.teleportTo(player.serverLevel(), scattered.x(), scattered.y(), scattered.z(), Set.of(), 0, 0);
                    }
                    continue;
                }
                if (!warnedAboutSpawn) {
                    SRE.LOGGER.warn("开局出生分散：地图 {} 的出生点 {} 附近没有安全站位，保留原出生行为。",
                            areas.mapName, originalSpawn);
                    warnedAboutSpawn = true;
                }
            }
            if (pos != null) {
                player.teleportTo(pos.x(), pos.y() + 1, pos.z());
            } else {
                Vec3 pos1 = player.position().add(areas.getPlayAreaOffset());
                player.teleportTo(player.serverLevel(), pos1.x(), pos1.y() + 1, pos1.z(), Set.of(), 0, 0);
            }
        }

        gameComponent.setJumpAvailable(areas.areasSettings.canJump);

        // 应用地图重力配置
        // 不要修改base用modifier！！！！！！！！！！
        for (ServerPlayer player : players) {
            var gravityAttr = player.getAttribute(Attributes.GRAVITY);
            if (gravityAttr != null) {
                AttributeModifier gravityModifier = new AttributeModifier(
                        SRE.id("map"),
                        areas.areasSettings.gravityModifier, AttributeModifier.Operation.ADD_VALUE);
                gravityAttr.addOrReplacePermanentModifier(gravityModifier);
            }
        }

        // 应用全局药水效果
        for (String effectStr : areas.areasSettings.mobEffects) {
            if (effectStr.isEmpty())
                continue;
            try {
                String[] parts = effectStr.split(",");
                if (parts.length >= 1) {
                    ResourceLocation effectId = ResourceLocation.parse(parts[0]);
                    int level = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
                    var effectHolder = BuiltInRegistries.MOB_EFFECT.getHolder(effectId).orElse(null);
                    if (effectHolder != null) {
                        for (ServerPlayer player : players) {
                            player.addEffect(new MobEffectInstance(
                                    effectHolder, Integer.MAX_VALUE, level, true, false, false));
                        }
                        SRE.LOGGER.info("Applied global effect: " + effectStr);
                    } else {
                        SRE.LOGGER.warn("Unknown effect: " + effectId);
                    }
                }
            } catch (Exception e) {
                SRE.LOGGER.error("Failed to apply effect: " + effectStr, e);
            }
        }
    }

    public static void setForcedReadyPlayers(Collection<UUID> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            forcedReadyPlayers = null;
            return;
        }
        forcedReadyPlayers = new LinkedHashSet<>(playerIds);
    }

    public static void clearForcedReadyPlayers() {
        forcedReadyPlayers = null;
    }

    private static List<ServerPlayer> getStartingPlayers(ServerLevel serverWorld) {
        ParticipationComponent participation = ParticipationComponent.KEY.get(serverWorld);
        if (forcedReadyPlayers != null && !forcedReadyPlayers.isEmpty()) {
            List<ServerPlayer> selected = forcedReadyPlayers.stream()
                    .map(serverWorld.getServer().getPlayerList()::getPlayer)
                    .filter(Objects::nonNull)
                    .filter(participation::isParticipating)
                    .toList();
            if (!selected.isEmpty()) {
                return selected;
            }
        }
        return getReadyPlayerList(serverWorld);
    }

    public static int getStartingPlayerCount(ServerLevel serverWorld) {
        return getStartingPlayers(serverWorld).size();
    }

    private static List<ServerPlayer> getReadyPlayerList(ServerLevel serverWorld) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        ParticipationComponent participation = ParticipationComponent.KEY.get(serverWorld);
        return serverWorld.getServer().getPlayerList().getPlayers().stream()
                .filter(participation::isParticipating)
                .filter(player -> participation.isSettlementViewer(player.getUUID())
                        || (player.serverLevel() == serverWorld && areas.getReadyArea().contains(player.position())))
                .toList();
    }

    public static void finalizeGame(ServerLevel world) {
        SREGameWorldComponent.getInstance(world).playerBannedBlockTime.clear();

        SRE.LOGGER.info("-".repeat(20));

        SRE.LOGGER.info("Game Stopped!");
        RefugeeComponent.KEY.get(world).reset();
        world.setWeatherParameters(6000, 0, false, false);
        serverTaskQueue.clear();
        serverAsynTaskLists.clear();

        isStartingGame = false;
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
        RoleMethodDispatcher.onEndGame(world);
        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(world);

        // var areasWorldComponent = AreasWorldComponent.KEY.get(world);
        gameComponent.isSkillAvailable = false;

        world.setDayTime(Level.TICKS_PER_DAY / 2);
        world.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, world.getServer());
        world.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, world.getServer());
        gameComponent.getGameMode().finalizeGame(world, gameComponent);
        OnGameEnd.EVENT.invoker().onGameEnd(world, gameComponent);

        TimeStopEffect.canMovePlayers.clear();
        SRE.REPLAY_MANAGER.finalizeReplay(roundEnd.getWinStatus(), roundEnd);
        // 对局结束后把完整回放时间线作为全局战绩异步保存到远端数据库（未开启 MySQL 同步时自动跳过）。
        net.exmo.sre.record.MatchRecordService.recordFinishedMatch(world);

        isGameStarted = false;

        gameComponent.getGameMode().recordWinStats(world, roundEnd, gameComponent);
        io.wifi.starrailexpress.settlement.SettlementSnapshot settlement = null;
        try {
            settlement = io.wifi.starrailexpress.settlement.SettlementManager.capture(world, roundEnd, gameComponent);
        } catch (RuntimeException exception) {
            SRE.LOGGER.error("无法冻结舞台结算，保留原文字回放", exception);
        }
        // --- 结束新增统计数据更新逻辑 (胜利/失败) ---
        // roundEnd.sync();
        // Show replay to all players
        gameComponent.getGameMode().showReplay(world, roundEnd, gameComponent);
        if (SREConfig.instance().logGameEvent) {
            SRE.LOGGER.info("-".repeat(20));
            SRE.LOGGER.info(ListRoleInRoundCommand.generateRoleInRoundText(world).getString().replaceAll("\n", "; "));
            SRE.LOGGER.info("-".repeat(20));
        }

        SREWorldBlackoutComponent.KEY.get(world).reset();
        SRETrainWorldComponent trainComponent = SRETrainWorldComponent.KEY.get(world);
        trainComponent.setSpeed(0);

        trainComponent.setTimeOfDay(SRETrainWorldComponent.TimeOfDay.NOON);

        resetEntities(world);

        // reset all players
        for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            // 不需要别的逻辑，会自己清掉修饰符+药水
            resetPlayerAfterGame(player);
        }
        HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
        RadioItem.RADIO_GROUP.clear();

        // reset game component
        roundEnd.CustomWinnerPlayers.clear();

        SREGameTimeComponent.KEY.get(world).reset();
        gameComponent.clearRoleMap(false);
        gameComponent.resetPerPlayerKills(); // 重置本局击杀数
        SREGameWorldComponent.perPlayerDarknessTime.clear();
        gameComponent.setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
        trainComponent.setTime(0);
        gameComponent.roleWorldComponent.sync();

        roundEnd.sync();

        if (AutoShutdownWhenNotRunningCommand.autoShutdownWhenGameNotRunning) {
            world.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("\n\n\n\n%s\n",
                            Component.translatable("sre.shutdown.waring", 30).withStyle(ChatFormatting.YELLOW)),
                    false);
            SRENetworkMessageUtils.sendCODSubtitleToAll(
                    Component.translatable("sre.shutdown.waring", 30).withStyle(ChatFormatting.YELLOW),
                    Component.translatable("sre.shutdown.waring", 30).withStyle(ChatFormatting.YELLOW), 30 * 20,
                    java.awt.Color.GREEN.getRGB(), false, SubtitleS2CPayload.POS_CENTER);
            AutoShutdownWhenNotRunningCommand.autoShutdownWhenGameNotRunning = false;
            serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(30 * 20, () -> {
                world.getServer().halt(false);
            }));
        }
        SRE.LOGGER.info("=".repeat(20));

        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(world);
        worldModifierComponent.clearAll();
        worldModifierComponent.sync();
        serverCacheKillState.clear();
        io.wifi.starrailexpress.settlement.SettlementManager.begin(world, settlement);
    }

}
