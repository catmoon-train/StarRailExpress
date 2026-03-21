package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.compat.TrainVoicePlugin;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import io.wifi.starrailexpress.api.replay.GameReplayData;
import net.exmo.sre.nametag.NameTagInventoryComponent;

import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.NameTagSyncPayload;
import org.agmas.noellesroles.repack.HSRItems;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.utils.EntityClearUtils;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.Lists;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.block.FoodPlatterBlock;
import io.wifi.starrailexpress.block.NeonPillarBlock;
import io.wifi.starrailexpress.block.NeonTubeBlock;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import io.wifi.starrailexpress.block.SprinklerBlock;
import io.wifi.starrailexpress.block.ToggleableFacingLightBlock;
import io.wifi.starrailexpress.block.TrimmedBedBlock;
import io.wifi.starrailexpress.block.VentHatchBlock;
import io.wifi.starrailexpress.block_entity.BeveragePlateBlockEntity;
import io.wifi.starrailexpress.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.block_entity.SprinklerBlockEntity;
import io.wifi.starrailexpress.block_entity.TrimmedBedBlockEntity;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.BartenderPlayerComponent;
import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameScoreboardComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREPlayerStatsComponent;
import io.wifi.starrailexpress.cca.SRETrainWorldComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.entity.FirecrackerEntity;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeath;
import io.wifi.starrailexpress.event.AfterShieldAllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.EarlyKillPlayer;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.event.OnPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayer;
import io.wifi.starrailexpress.event.OnPlayerKilledPlayerIdentifier;
import io.wifi.starrailexpress.event.OnShieldBroken;
import io.wifi.starrailexpress.event.OnTeammateKilledTeammate;
import io.wifi.starrailexpress.event.OnTrainAreaHaveReseted;
import io.wifi.starrailexpress.event.ShouldDropOnDeath;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.network.BreakArmorPayload;
import io.wifi.starrailexpress.network.CloseUiPayload;
import io.wifi.starrailexpress.network.TriggerScreenEdgeEffectPayload;
import io.wifi.starrailexpress.network.original.AnnounceEndingPayload;
import io.wifi.starrailexpress.util.TMMItemUtils;
import io.wifi.starrailexpress.DeathInfo;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

public class GameUtils {
    public static HashMap<BlockPos, Integer> taskBlocks = new HashMap<>();
    public static ArrayList<BlockPos> resetPoints = new ArrayList<>();
    public static ArrayList<ServerTaskInfoClasses.ServerTaskInfo> serverTaskQueue = new ArrayList<>();
    public static ArrayList<ServerTaskInfoClasses.ServerTaskInfo> serverAsynTaskLists = new ArrayList<>();
    public static boolean isStartingGame = false;

    public static void limitPlayerToBox(ServerPlayer player, AABB box) {
        Vec3 playerPos = player.position();
        Vec3 teleportPos = playerPos;

        if (!box.contains(playerPos)) {
            double x = playerPos.x();
            double y = playerPos.y();
            double z = playerPos.z();
            double bounceFactor = 0.2; // 反弹系数，可根据需要调整

            // Z轴边界检测和反弹
            if (z < box.minZ - 50) {
                z = box.minZ - 50;
                // 添加Z轴正方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0, bounceFactor));
            }
            if (z > box.maxZ + 50) {
                z = box.maxZ + 50;
                // 添加Z轴负方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(0, 0, -bounceFactor));
            }

            // Y轴边界检测和反弹
            if (y < box.minY - 20) {
                y = box.minY - 20;
                // 添加Y轴正方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(0, bounceFactor, 0));
            }
            if (y > box.maxY + 20) {
                y = box.maxY + 20;
                // 添加Y轴负方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(0, -bounceFactor, 0));
            }

            // X轴边界检测和反弹
            if (x < box.minX - 50) {
                x = box.minX - 50;
                // 添加X轴正方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(bounceFactor, 0, 0));
            }
            if (x > box.maxX + 50) {
                x = box.maxX + 50;
                // 添加X轴负方向的反弹速度
                player.setDeltaMovement(player.getDeltaMovement().add(-bounceFactor, 0, 0));
            }

            teleportPos = new Vec3(x, y, z);
        }

        // 只有在玩家位置需要调整时才进行传送
        if (!teleportPos.equals(playerPos)) {
            player.teleportTo(teleportPos.x(), teleportPos.y(), teleportPos.z());
        }
    }

    public static void executeCommand(CommandSourceStack source, String command) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, command);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute: " + command + ", error: " + e.getMessage());
        }
    }

    public static void startGame(ServerLevel world, GameMode gameMode, int time) {
        if (SRE.isLobby || isStartingGame)
            return;
        if (SREGameWorldComponent.KEY.get(world).isRunning()) {
            return;
        }
        isStartingGame = true;
        MapResetManager.loadArea(world);
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        if (areas.noReset) {
            SRE.LOGGER.info("NO RESET MAP!");
            trueStartGame(world, gameMode, time);
            return;
        }
        if (SREConfig.instance().enableAutoTrainReset) {
            var task = new ServerTaskInfoClasses.AutoTrainResetTask(areas, world, gameMode, time);
            serverTaskQueue.add(task);
        } else {
            var task = new ServerTaskInfoClasses.OnlySomeBlockResetTask(resetPoints, world, gameMode, time);
            serverTaskQueue.add(task);
        }
    }

    public static void registerEventForServerTickForDoingResetTasks() {
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (!serverTaskQueue.isEmpty()) {
                // int size = serverTaskQueue.size();
                int i = 0;
                {
                    var task = serverTaskQueue.get(i);
                    if (!task.finished && task.onTick(server)) {
                        task.finished = true;
                        if (!task.cancelled)
                            task.onFinished();
                    }
                }
                serverTaskQueue.removeIf((t) -> t.finished || t.cancelled);
            }
            if (!serverAsynTaskLists.isEmpty()) {
                int size = serverAsynTaskLists.size();
                for (int i = 0; i < size; i++) {
                    var task = serverAsynTaskLists.get(i);
                    if (!task.finished && task.onTick(server)) {
                        task.finished = true;
                        if (!task.cancelled)
                            task.onFinished();
                    }
                }
                serverAsynTaskLists.removeIf((t) -> t.finished || t.cancelled);
            }
        });
    }

    public static void trueStartGame(ServerLevel world, GameMode gameMode, int time) {
        if (SRE.isLobby)
            return;
        // 延迟5s
        SRE.LOGGER.info("Game Started!");
        serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(100, () -> {
            resetEntities(world);
        }));
        executeFunction(world.getServer().createCommandSourceStack(), "harpymodloader:early_start_game");
        executeFunction(world.getServer().createCommandSourceStack(),
                "harpymodloader:early_start_game_" + MapManager.last_start_map);
        for (ServerPlayer player : world.players()) {
            ServerPlayNetworking.send(player, new CloseUiPayload());
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(world);
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        int playerCount = Math.toIntExact(world.players().stream()
                .filter(serverPlayerEntity -> (areas.getReadyArea().contains(serverPlayerEntity.position()))).count());
        game.setGameMode(gameMode);
        SREGameTimeComponent.KEY.get(world).setResetTime(time);
        RefugeeComponent.KEY.get(world).reset();
        if (playerCount >= gameMode.minPlayerCount) {
            game.setGameStatus(SREGameWorldComponent.GameStatus.STARTING);

            // 初始化计分板组件
            SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY
                    .get(world.getServer().getScoreboard());
            scoreboardComponent.reset();

        } else {
            for (ServerPlayer player : world.players()) {
                player.displayClientMessage(
                        Component.translatable("game.start_error.not_enough_players", gameMode.minPlayerCount), true);
            }
            isStartingGame = false;
        }
    }

    public static void stopGame(ServerLevel world) {
        SREGameWorldComponent component = SREGameWorldComponent.KEY.get(world);
        component.ticksUntilNextResetAttempt = 0;
        component.setGameStatus(SREGameWorldComponent.GameStatus.STOPPING);
    }

    private static void executeFunction(CommandSourceStack source, String function) {
        try {
            source.getServer().getCommands().performPrefixedCommand(source, "function " + function);
        } catch (Exception e) {
            Log.warn(LogCategory.GENERAL, "Failed to execute function: " + function + ", error: " + e.getMessage());
        }
    }

    public static void initializeGame(ServerLevel serverWorld) {
        isStartingGame = false;

        SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
        gameComponent.isSkillAvailable = true;
        // AreasWorldComponent areasWorldComponent =
        // AreasWorldComponent.KEY.get(serverWorld);

        RoleMethodDispatcher.onStartGame(serverWorld);
        List<ServerPlayer> readyPlayerList = getReadyPlayerList(serverWorld);
        // serverWorld.setWeatherParameters(0,-1, true, true);
        baseInitialize(serverWorld, gameComponent, readyPlayerList);

        // 在分配角色前将所有玩家设置为冒险模式，并且resetPlayer
        for (ServerPlayer player : serverWorld.players()) {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        }

        // 先分配角色,然后再初始化回放管理器
        gameComponent.getGameMode().initializeGame(serverWorld, gameComponent, readyPlayerList);

        // 初始化回放管理器,此时角色已经分配完成
        SRE.REPLAY_MANAGER.initializeReplay(readyPlayerList, gameComponent.getRoles());
        // 记录游戏开始事件
        SRE.REPLAY_MANAGER.addEvent(GameReplayData.EventType.GAME_START, null, null, null, null);

        // Update replay with actual roles after assignment
        SRE.REPLAY_MANAGER.updateRolesFromComponent(gameComponent);

        // Set game status to ACTIVE after roles are assigned
        gameComponent.setGameStatus(SREGameWorldComponent.GameStatus.ACTIVE);

        gameComponent.sync();

        // 初始化计分板组件
        SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY
                .get(serverWorld.getServer().getScoreboard());
        scoreboardComponent.reset();

        // 设置平民获胜所需的总任务数 (这里可以根据玩家数量动态计算)
        int totalRequiredTasks = readyPlayerList.size() * 5; // 每个玩家需要完成5个任务
        scoreboardComponent.setTotalRequiredTasks(totalRequiredTasks);

        // 更新所有玩家的计分板显示
        scoreboardComponent.updateAllPlayerScores();

        // --- 新增统计数据更新逻辑 ---
        for (ServerPlayer player : readyPlayerList) {

            SREPlayerStatsComponent stats = SREPlayerStatsComponent.KEY.get(player);
            stats.incrementTotalGamesPlayed();
            SRERole playerRole = gameComponent.getRole(player);
            if (playerRole != null) {
                stats.getOrCreateRoleStats(playerRole.identifier()).incrementTimesPlayed();

                // 统计阵营场次
                if (playerRole.isVigilanteTeam()) {
                    stats.incrementTotalSheriffGames();
                } else if (playerRole.canUseKiller()) {
                    stats.incrementTotalKillerGames();
                } else if (playerRole.isNeutrals()) {
                    stats.incrementTotalNeutralGames();
                } else if (playerRole.isInnocent() && !playerRole.isVigilanteTeam()) {
                    stats.incrementTotalCivilianGames();
                }
            }
        }
        addItemCooldowns(serverWorld);
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(serverWorld);
        // --- 结束新增统计数据更新逻辑 ---
        executeFunction(serverWorld.getServer().createCommandSourceStack(),
                "harpymodloader:start_game_" + AreasWorldComponent.KEY.get(serverWorld).mapName);
        OnTrainAreaHaveReseted.EVENT.invoker().onWorldHaveReseted(serverWorld);
    }

    public static void addItemCooldowns(ServerLevel world) {
        for (ServerPlayer player : world.players()) {

            var cooldowns = player.getCooldowns();
            var items = new ArrayList<>(MCItemsUtils.getItemsByTag(player.serverLevel(), TMMItemTags.GUNS));
            // Noellesroles.LOGGER.info("itemSize:" + items.size());
            int SAFE_TIME_COOLDOWN = SREConfig.instance().safeTimeCooldown * 20;
            items.forEach((item) -> {
                cooldowns.addCooldown(item,
                        (Integer) SAFE_TIME_COOLDOWN);
            });
            cooldowns.addCooldown(TMMItems.GRENADE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(TMMItems.PSYCHO_MODE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(ModItems.SP_KNIFE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(ModItems.STALKER_KNIFE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(TMMItems.NUNCHUCK, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(TMMItems.KNIFE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(ModItems.FAKE_REVOLVER, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(Items.CLOCK, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(HSRItems.TOXIN, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(HSRItems.ANTIDOTE, SAFE_TIME_COOLDOWN);
            cooldowns.addCooldown(TMMItems.SNIPER_RIFLE, SAFE_TIME_COOLDOWN);
            player.addEffect(new MobEffectInstance(
                    ModEffects.NO_COLLIDE,
                    (int) (SAFE_TIME_COOLDOWN), // 持续时间 30s（tick）
                    0, // 等级（0 = 速度 I）
                    true, // ambient（环境效果，如信标）
                    false, // showParticles（显示粒子）
                    false // showIcon（显示图标）
            ));
            player.addEffect(new MobEffectInstance(
                    ModEffects.SKILL_BANED,
                    (int) (SAFE_TIME_COOLDOWN), // 持续时间 30s（tick）
                    0, // 等级（0 = 速度 I）
                    true, // ambient（环境效果，如信标）
                    false, // showParticles（显示粒子）
                    false // showIcon（显示图标）
            ));
        }
    }

    public static Vec3 getSpawnPos(AreasWorldComponent areas, int room) {
        // Try to get position from configured room positions
        Vec3 configuredPos = areas.getRoomPosition(room);
        if (configuredPos != null) {
            return configuredPos;
        }

        // Fallback to default positions based on room count
        // int roomCount = areas.getRoomCount();
        // if (roomCount >= 7) {
        // if (room == 1) {
        // return new Vec3(116, 122, -539);
        // } else if (room == 2) {
        // return new Vec3(124, 122, -534);
        // } else if (room == 3) {
        // return new Vec3(131, 122, -534);
        // } else if (room == 4) {
        // return new Vec3(144, 122, -540);
        // } else if (room == 5) {
        // return new Vec3(119, 128, -537);
        // } else if (room == 6) {
        // return new Vec3(132, 128, -536);
        // } else if (room == 7) {
        // return new Vec3(146, 128, -537);
        // }
        // } else if (roomCount >= 4) {
        // // Handle 4-6 rooms
        // switch (room) {
        // case 1: return new Vec3(116, 122, -539);
        // case 2: return new Vec3(124, 122, -534);
        // case 3: return new Vec3(131, 122, -534);
        // case 4: return new Vec3(144, 122, -540);
        // }
        // } else if (roomCount >= 2) {
        // // Handle 2-3 rooms
        // switch (room) {
        // case 1: return new Vec3(116, 122, -539);
        // case 2: return new Vec3(131, 122, -534);
        // case 3: return new Vec3(144, 122, -540);
        // }
        // } else if (roomCount == 1) {
        // // Handle single room
        // return new Vec3(131, 122, -534);
        // }
        return null;
    }

    public static long startTime = 0;
    public static Map<UUID, Integer> roomToPlayer = new HashMap<>();
    // public static Map<TaskPointType, List<BlockPos>> taskPoints = new
    // HashMap<>();

    private static void baseInitialize(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            List<ServerPlayer> players) {
        if (SRE.isLobby)
            return;
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        startTime = System.currentTimeMillis();

        SRETrainWorldComponent.KEY.get(serverWorld).reset();
        SREWorldBlackoutComponent.KEY.get(serverWorld).reset();
        serverWorld.setDayTime(SRETrainWorldComponent.TimeOfDay.SUNDOWN.time);
        serverWorld.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, serverWorld.getServer());
        serverWorld.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, serverWorld.getServer());
        serverWorld.setWeatherParameters(6000, 0, false, false);

        // serverWorld.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,
        // serverWorld.getServer());

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
            if (!nameTagInventoryComponent.CurrentNameTag.isEmpty()) {
                nameTagMap.put(player.getUUID(), nameTagInventoryComponent.CurrentNameTag);
            }
            player.removeVehicle();
            resetPlayer(player);
        }
        NameTagSyncPayload payload = new NameTagSyncPayload(nameTagMap);
        for (ServerPlayer player : players) {
            ServerPlayNetworking.send(player, payload);
        }

        // teleport players to play area

        // teleport non playing players
        for (ServerPlayer player : serverWorld
                .getPlayers(serverPlayerEntity -> !players.contains(serverPlayerEntity))) {

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
        for (ServerPlayer serverPlayerEntity : players) {
            ItemStack itemStack = new ItemStack(TMMItems.KEY);
            roomNumber = i % roomCount + 1;
            int finalRoomNumber = roomNumber;
            itemStack.update(DataComponents.LORE, ItemLore.EMPTY, component -> new ItemLore(Component
                    .nullToEmpty("Room " + finalRoomNumber)
                    .toFlatList(Style.EMPTY.withItalic(false).withColor(0xFF8C00))));
            serverPlayerEntity.addItem(itemStack);
            roomToPlayer.put(serverPlayerEntity.getUUID(), finalRoomNumber);

            // give letter
            ItemStack letter = new ItemStack(TMMItems.INIT_ITEMS.LETTER);
            if (TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc != null) {
                TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc.accept(letter, serverPlayerEntity);
            } else {
                letter.set(DataComponents.ITEM_NAME, Component.translatable(letter.getDescriptionId()));
                int letterColor = 0xC5AE8B;
                String tipString = "tip.letter.";
                letter.update(DataComponents.LORE, ItemLore.EMPTY, component -> {
                    List<Component> text = new ArrayList<>();
                    UnaryOperator<Style> stylizer = style -> style.withItalic(false).withColor(letterColor);

                    Component displayName = serverPlayerEntity.getDisplayName();
                    String string = displayName != null ? displayName.getString()
                            : serverPlayerEntity.getName().getString();
                    if (string.charAt(string.length() - 1) == '\uE780') { // remove ratty supporter icon
                        string = string.substring(0, string.length() - 1);
                    }

                    text.add(Component.translatable(tipString + "name", string)
                            .withStyle(style -> style.withItalic(false).withColor(0xFFFFFF)));
                    text.add(Component.translatable(tipString + "room").withStyle(stylizer));
                    text.add(Component.translatable(tipString + "tooltip1",
                            Component.translatable(tipString + "room." + switch (finalRoomNumber) {
                                case 1 -> "grand_suite";
                                case 2, 3 -> "cabin_suite";
                                default -> "twin_cabin";
                            }).getString()).withStyle(stylizer));
                    text.add(Component.translatable(tipString + "tooltip2").withStyle(stylizer));

                    return new ItemLore(text);
                });
            }

            serverPlayerEntity.addItem(letter);
            i++;
        }
        for (ServerPlayer player : players) {
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            //
            Vec3 pos = getSpawnPos(areas, roomToPlayer.getOrDefault(player.getUUID(), 1));
            if (pos != null) {
                player.teleportTo(pos.x(), pos.y() + 1, pos.z());
            } else {
                Vec3 pos1 = player.position().add(areas.getPlayAreaOffset());
                player.teleportTo(player.serverLevel(), pos1.x(), pos1.y() + 1, pos1.z(), Set.of(), 0, 0);
            }
        }

        // Don't set game status to ACTIVE here - it will be set after roles are
        // assigned in initializeGame()
        // Create a copy of entities to avoid concurrent modification issues
        List<net.minecraft.world.entity.Entity> entitiesToDiscard = new ArrayList<>();
        serverWorld.getAllEntities().forEach(entity -> {
            if (entity instanceof ItemEntity) {
                entitiesToDiscard.add(entity);
            }
        });
        entitiesToDiscard.forEach(net.minecraft.world.entity.Entity::discard);

        gameComponent.setJumpAvailable(areas.canJump);
        gameComponent.setOutsideSoundsAvailable(areas.haveOutsideSound);
    }

    private static List<ServerPlayer> getReadyPlayerList(ServerLevel serverWorld) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
        return serverWorld
                .getPlayers(serverPlayerEntity -> areas.getReadyArea().contains(serverPlayerEntity.position()));
    }

    public static ArrayList<Predicate<Entry<Player, String>>> CustomWinnersPredicates = new ArrayList<>();

    public static void finalizeGame(ServerLevel world) {
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
        gameComponent.getGameMode().finalizeGame(world, gameComponent);
        OnGameEnd.EVENT.invoker().onGameEnd(world, gameComponent);
        SRE.REPLAY_MANAGER.finalizeReplay(roundEnd.getWinStatus(), roundEnd);

        // --- 新增统计数据更新逻辑 (胜利/失败) ---
        GameUtils.WinStatus winStatus = roundEnd.getWinStatus();
        SREWorldBlackoutComponent.KEY.get(world).reset();
        // 修复4: 检查是否为恋人胜利
        boolean isLoversWin = winStatus == WinStatus.LOVERS;

        for (ServerPlayer player : world.players()) {
            SREPlayerStatsComponent stats = SREPlayerStatsComponent.KEY.get(player);
            SRERole playerRole = gameComponent.getRole(player);
            if (playerRole == null)
                continue;
            boolean isWinner = false;
            switch (winStatus) {
                case CUSTOM:
                case CUSTOM_COMPONENT:
                    String roleIdentifier = playerRole.identifier().getPath();
                    if (roundEnd.CustomWinnerID != null && roundEnd.CustomWinnerID.equals(roleIdentifier)) {
                        isWinner = true;
                    }
                    // 保留原有的 CustomWinnersPredicates 作为备用
                    else if (CustomWinnersPredicates.stream().anyMatch((pred) -> {
                        return pred.test(Map.entry(player, roundEnd.CustomWinnerID));
                    })) {
                        isWinner = true;
                    }
                    break;
                case GAMBLER:
                    if (playerRole.identifier().getPath().equals("gambler")) {
                        isWinner = true;
                    }
                    break;
                case KILLERS:
                    if (SREGameWorldComponent.isKillerTeamRoleStatic(playerRole) && !playerRole.isInnocent()) {
                        // String roleidentifier = playerRole.identifier().getPath();
                        // 魔术师不算胜利
                        isWinner = true;
                    }
                    break;
                case LOOSE_END:
                    if (winStatus == WinStatus.LOOSE_END) {
                        if (SRE.GAME.identifier.equals(SREGameModes.LOOSE_ENDS.identifier)) {
                            if (player.getUUID().equals(gameComponent.getLooseEndWinner())) {
                                isWinner = true;
                            }
                        } else {
                            if (playerRole.identifier().equals(TMMRoles.LOOSE_END.identifier())) {
                                isWinner = true;
                            }
                        }
                    }
                    break;
                case NIAN_SHOU:
                    if (playerRole.identifier().getPath().equals("nianshou")) {
                        isWinner = true;
                    }
                    break;
                case LOVERS:
                    if (roundEnd.CustomWinnerPlayers != null
                            && roundEnd.CustomWinnerPlayers.contains(player.getUUID())) {
                        isWinner = true;
                    }
                    break;
                case TIME:
                case PASSENGERS:
                    if (playerRole.isInnocent())
                        isWinner = true;
                    else {
                        String roleidentifier = playerRole.identifier().getPath();
                        if ("amnesiac".equals(roleidentifier) || "initiate".equals(roleidentifier)) {
                            isWinner = true;
                        }
                        // 魔术师不需要判断，因为他是innocent
                    }
                    break;
                case RECORDER:
                    if (playerRole.identifier().getPath().equals("recorder")) {
                        isWinner = true;
                    }
                    break;
                default:
                    break;

            }
            // 修复4: 恋人获胜时单独统计恋人胜利
            if (isLoversWin && roundEnd.CustomWinnerPlayers != null
                    && roundEnd.CustomWinnerPlayers.contains(player.getUUID())) {
                isWinner = true;
            }

            if (isWinner) {
                roundEnd.setPlayerWin(player.getUUID(), isWinner);
                roundEnd.CustomWinnerPlayers.add(player.getUUID());
                stats.incrementTotalWins();
                if (playerRole != null) {
                    stats.getOrCreateRoleStats(playerRole.identifier()).incrementWinsAsRole();

                    // 统计阵营胜利
                    if (playerRole.isVigilanteTeam()) {
                        stats.incrementTotalSheriffWins();
                    } else if (playerRole.canUseKiller()) {
                        stats.incrementTotalKillerWins();
                    } else if (playerRole.isNeutrals()) {
                        stats.incrementTotalNeutralWins();
                    } else if (playerRole.isInnocent() && !playerRole.isVigilanteTeam()) {
                        stats.incrementTotalCivilianWins();
                    }
                }
                // 修复4: 恋人胜利时额外统计恋人胜利次数
                if (isLoversWin && roundEnd.CustomWinnerPlayers != null
                        && roundEnd.CustomWinnerPlayers.contains(player.getUUID())) {
                    stats.incrementTotalLoversWins();
                }
            } else {
                stats.incrementTotalLosses();
                if (playerRole != null) {
                    stats.getOrCreateRoleStats(playerRole.identifier()).incrementLossesAsRole();
                }
            }
        }
        // --- 结束新增统计数据更新逻辑 (胜利/失败) ---
        // roundEnd.sync();
        // Show replay to all players
        for (ServerPlayer player : world.players()) {
            SRE.REPLAY_MANAGER.showReplayToPlayer(player);
        }

        SREWorldBlackoutComponent.KEY.get(world).reset();
        SRETrainWorldComponent trainComponent = SRETrainWorldComponent.KEY.get(world);
        trainComponent.setSpeed(0);

        trainComponent.setTimeOfDay(SRETrainWorldComponent.TimeOfDay.NOON);

        resetEntities(world);

        // reset all players
        for (ServerPlayer player : world.players()) {
            resetPlayerAfterGame(player);
        }

        // reset game component
        roundEnd.CustomWinnerPlayers.clear();

        SREGameTimeComponent.KEY.get(world).reset();
        gameComponent.clearRoleMap();
        gameComponent.setGameStatus(SREGameWorldComponent.GameStatus.INACTIVE);
        trainComponent.setTime(0);
        gameComponent.sync();

        // 重置计分板组件
        SREGameScoreboardComponent scoreboardComponent = SREGameScoreboardComponent.KEY
                .get(world.getServer().getScoreboard());
        scoreboardComponent.reset();
        roundEnd.sync();
    }

    public static void resetPlayer(ServerPlayer player) {
        TMMItemUtils.clearItem(player, (item) -> true, -1);
        SREPlayerMoodComponent.KEY.get(player).init();
        SREPlayerShopComponent.KEY.get(player).init();
        SREPlayerPoisonComponent.KEY.get(player).init();
        SREPlayerPsychoComponent.KEY.get(player).init();
        SREPlayerNoteComponent.KEY.get(player).init();
        BartenderPlayerComponent.KEY.get(player).init();
        if (!TrainVoicePlugin.isVoiceChatMissing()) {
            TrainVoicePlugin.resetPlayer(player.getUUID());
        }

        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        player.stopSleeping();
        ExtraSlotComponent.KEY.get(player).clear();
    }

    public static void resetPlayerAfterGame(ServerPlayer player) {
        resetPlayer(player);
        ServerPlayNetworking.send(player, new AnnounceEndingPayload());

        player.removeVehicle();

        AreasWorldComponent.PosWithOrientation spawnPos = AreasWorldComponent.KEY.get(player.level()).getSpawnPos();
        if (spawnPos == null) {
            BlockPos worldSpawnPos = player.serverLevel().getSharedSpawnPos();
            float worldSpawnAngle = player.serverLevel().getSharedSpawnAngle();
            var spawnPosVec3 = new Vec3(worldSpawnPos.getX(), worldSpawnPos.getY(), worldSpawnPos.getZ());
            spawnPos = new AreasWorldComponent.PosWithOrientation(spawnPosVec3, worldSpawnAngle, 0);
        }
        DimensionTransition teleportTarget = new DimensionTransition(player.serverLevel(), spawnPos.pos, Vec3.ZERO,
                spawnPos.yaw, spawnPos.pitch, DimensionTransition.DO_NOTHING);
        player.changeDimension(teleportTarget);
    }

    public static void resetAllToilets(ServerLevel serverWorld) {
        // Use the same method as train reset to iterate through loaded chunks
        for (int x = serverWorld.getMinSection(); x <= serverWorld.getMaxSection(); x++) {
            for (int z = serverWorld.getMinSection(); z <= serverWorld.getMaxSection(); z++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = serverWorld.getChunk(x, z);
                if (chunk != null) {
                    for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : chunk.getBlockEntities()
                            .values()) {
                        if (blockEntity instanceof io.wifi.starrailexpress.block_entity.ToiletBlockEntity toiletEntity) {
                            toiletEntity.reset();
                        }
                    }
                }
            }
        }
    }

    public static boolean isPlayerEliminated(Player player) {
        if (isPlayerSplitPersonalityAndSurvive(player) == SPAliveResult.ALIVE)
            return false;
        return player == null || !player.isAlive() || player.isCreative() || player.isSpectator();
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer) {
        killPlayer(victim, spawnBody, killer, GameConstants.DeathReasons.GENERIC);
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        killPlayer(victim, spawnBody, killer, deathReason, false);
    }

    public static void forceKillPlayer(Player victim, boolean spawnBody, @Nullable Player killer,
            ResourceLocation deathReason) {
        killPlayer(victim, spawnBody, killer, deathReason, true);
    }

    public static void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        Player trueKiller = EarlyKillPlayer.FIND_KILLER_EVENT.invoker().findTrueKiller(victim, _killer, deathReason);
        Player killer;
        if (trueKiller != null)
            killer = trueKiller;
        else
            killer = _killer;
        _killer = killer;
        SREPlayerPsychoComponent component = SREPlayerPsychoComponent.KEY.get(victim);
        if (killer != null && killer instanceof ServerPlayer serverPlayer) {
            final var triggerScreenEdgeEffectPayload = new TriggerScreenEdgeEffectPayload(Color.WHITE.getRGB(), 600,
                    0.6f);
            ServerPlayNetworking.send(serverPlayer, triggerScreenEdgeEffectPayload);
        }

        boolean canDeath = true;
        Component deathMessageComponent = Component.translatable("message.death_reason.null");
        if (victim instanceof ServerPlayer serverVictim) {
            deathMessageComponent = SRE.REPLAY_MANAGER.recordPlayerKill(killer != null ? killer.getUUID() : null,
                    serverVictim.getUUID(),
                    deathReason);
        }

        // Check if victim has a role assigned - if not, skip role-dependent logic
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        if (gameWorldComponent.getRole(victim) == null) {
            // Player doesn't have a role (game not started or joined mid-game), don't kill
            // them
            return;
        }
        if (killer != null) {
            if (killer instanceof ServerPlayer spkiller) {
                if (victim instanceof ServerPlayer spvictim) {
                    OnPlayerKilledPlayer.DeathReason eventDeathReason;
                    switch (deathReason.getPath()) {
                        case "derringer_shot":
                        case "revolver_shot":
                        case "gun_shot":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.GUN_SHOOT;
                            break;
                        case "knife_stab":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.KNIFE;
                            break;
                        case "grenade":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.GRENADE;
                            break;
                        case "bat_hit":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.BAT;
                            break;
                        case "poison":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.POISON;
                            break;
                        case "arrow":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.ARROW;
                            break;
                        case "trident":
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.TRIDENT;
                            break;
                        default:
                            eventDeathReason = OnPlayerKilledPlayer.DeathReason.UNKNOWN;
                    }
                    // LoggerFactory.getLogger("death_Reason").info(deathReason.getPath());
                    OnPlayerKilledPlayer.EVENT.invoker().playerKilled(spvictim, spkiller, eventDeathReason);
                    OnPlayerKilledPlayerIdentifier.EVENT.invoker().playerKilled(spvictim, spkiller, deathReason);
                }
            }
        }

        if (!AllowPlayerDeath.EVENT.invoker().allowDeath(victim, deathReason))
            if (!forceDeath)
                return;
        if (!AllowPlayerDeathWithKiller.EVENT.invoker().allowDeath(victim, killer, deathReason))
            if (!forceDeath)
                return;
        if (killer != null) {
            if (killer instanceof ServerPlayer spkiller) {
                BartenderPlayerComponent bartenderPlayerComponent = BartenderPlayerComponent.KEY.get(victim);
                if (bartenderPlayerComponent != null) {
                    if (bartenderPlayerComponent.getArmor() > 0) {
                        boolean cantDefend = SRE.canStickArmor.stream().anyMatch((pre) -> {
                            return pre.test(new DeathInfo(victim, killer, deathReason));
                        });
                        if (!cantDefend) {
                            victim.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR,
                                    SoundSource.MASTER, 5.0F, 1.0F);
                            bartenderPlayerComponent.removeArmor();
                            SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                            ServerPlayNetworking.send(spkiller,
                                    new BreakArmorPayload(victim.getX(), victim.getY(), victim.getZ()));
                            OnShieldBroken.EVENT.invoker().onShieldBroken(victim, killer);
                            if (!forceDeath)
                                return;
                        }
                    }
                }
            }
        }

        if (component.getPsychoTicks() > 0) {
            if (component.getArmour() > 0) {
                component.setArmour(component.getArmour() - 1);
                if (SRE.REPLAY_MANAGER != null) {
                    SRE.REPLAY_MANAGER.breakArmor(victim.getUUID());
                }

                if (killer instanceof ServerPlayer serverPlayer) {
                    ServerPlayNetworking.send(serverPlayer,
                            new BreakArmorPayload(victim.getX(), victim.getY(), victim.getZ()));
                }
                component.sync();
                victim.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5F, 1F);
                return;
            } else {
                component.stopPsycho();
            }
        }
        if (!AfterShieldAllowPlayerDeath.EVENT.invoker().allowDeath(victim, deathReason))
            if (!forceDeath)
                return;
        if (!AfterShieldAllowPlayerDeathWithKiller.EVENT.invoker().allowDeath(victim, killer, deathReason))
            if (!forceDeath)
                return;
        // --- 新增统计数据更新逻辑 (击杀者) ---
        if (killer instanceof ServerPlayer serverKiller) {
            SREPlayerStatsComponent killerStats = SREPlayerStatsComponent.KEY.get(serverKiller);
            killerStats.incrementTotalKills();
            SRERole killerRole = gameWorldComponent.getRole(serverKiller);
            if (killerRole != null) {
                canDeath = killerRole.onKill(victim, spawnBody, killer, deathReason);
                killerStats.getOrCreateRoleStats(killerRole.identifier()).incrementKillsAsRole();
                // 更新阵营击杀数
                if (killerRole.isVigilanteTeam()) {
                    killerStats.incrementTotalSheriffKills();
                } else if (killerRole.canUseKiller()) {
                    killerStats.incrementTotalKillerKills();
                } else if (killerRole.isNeutrals()) {
                    killerStats.incrementTotalNeutralKills();
                } else if (killerRole.isInnocent() && !killerRole.isVigilanteTeam()) {
                    killerStats.incrementTotalCivilianKills();
                }
                // 检测是否为友军击杀
                if (victim instanceof ServerPlayer serverVictim) {
                    SRERole victimRole = gameWorldComponent.getRole(serverVictim);
                    if (victimRole != null) {
                        boolean isTeamKill = false;
                        // 杀手击杀杀手
                        if (killerRole.canUseKiller() && victimRole.canUseKiller()) {
                            isTeamKill = true;
                            OnTeammateKilledTeammate.EVENT.invoker().playerKilled(serverVictim, serverKiller, false,
                                    deathReason);
                        }
                        // 无辜者击杀无辜者
                        else if (killerRole.isInnocent() && victimRole.isInnocent()) {
                            isTeamKill = true;
                            OnTeammateKilledTeammate.EVENT.invoker().playerKilled(serverVictim, serverKiller, true,
                                    deathReason);
                        }
                        if (isTeamKill) {
                            killerStats.incrementTotalTeamKills();
                            killerStats.getOrCreateRoleStats(killerRole.identifier()).incrementTeamKillsAsRole();
                        }
                    }
                }
            }
        }
        // --- 结束新增统计数据更新逻辑 (击杀者) ---

        // --- 结束新增统计数据更新逻辑 (受害者) ---
        if (canDeath) {
            // --- 新增统计数据更新逻辑 (受害者) ---
            if (victim instanceof ServerPlayer serverVictim) {
                SRERole victimRole = gameWorldComponent.getRole(serverVictim);
                canDeath = victimRole.onDeath(victim, spawnBody, killer, deathReason);
                SREPlayerStatsComponent victimStats = SREPlayerStatsComponent.KEY.get(serverVictim);
                victimStats.incrementTotalDeaths();
                if (victimRole != null) {
                    victimStats.getOrCreateRoleStats(victimRole.identifier()).incrementDeathsAsRole();
                    // 更新阵营死亡数
                    if (victimRole.isVigilanteTeam()) {
                        victimStats.incrementTotalSheriffDeaths();
                    } else if (victimRole.canUseKiller()) {
                        victimStats.incrementTotalKillerDeaths();
                    } else if (victimRole.isNeutrals()) {
                        victimStats.incrementTotalNeutralDeaths();
                    } else if (victimRole.isInnocent() && !victimRole.isVigilanteTeam()) {
                        victimStats.incrementTotalCivilianDeaths();
                    }
                }
            }
            if (spawnBody) {
                PlayerBodyEntity body = TMMEntities.PLAYER_BODY.create(victim.level());
                if (body != null) {
                    if (killer != null) {
                        body.setKillerUuid(killer.getUUID());
                    }
                    body.setDeathReason(deathReason.toString());
                    body.setPlayerUuid(victim.getUUID());
                    Vec3 spawnPos = victim.position().add(victim.getLookAngle().normalize().scale(1));
                    body.moveTo(spawnPos.x(), victim.getY(), spawnPos.z(), victim.getYHeadRot(), 0f);
                    body.setYRot(victim.getYHeadRot());
                    body.setYHeadRot(victim.getYHeadRot());
                    victim.level().addFreshEntity(body);

                    {
                        if (gameWorldComponent.getRole(victim) != null) {
                            final var bodyDeathReasonComponent = BodyDeathReasonComponent.KEY.get(body);
                            bodyDeathReasonComponent.playerRole = gameWorldComponent.getRole(victim).identifier();
                            bodyDeathReasonComponent.sync();
                        }
                    }
                }
            }
            if (victim instanceof ServerPlayer serverPlayerEntity && isPlayerAliveAndSurvival(serverPlayerEntity)) {
                serverPlayerEntity.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                OnPlayerDeath.EVENT.invoker().onPlayerDeath(victim, deathReason);
                OnPlayerDeathWithKiller.EVENT.invoker().onPlayerDeath(victim, killer, deathReason);
                SREPlayerPoisonComponent poisonComponent = SREPlayerPoisonComponent.KEY.maybeGet(serverPlayerEntity)
                        .orElse(null);
                BartenderPlayerComponent bartenderPlayerComponent = BartenderPlayerComponent.KEY
                        .maybeGet(serverPlayerEntity)
                        .orElse(null);
                // 删除玩家死后中毒
                if (poisonComponent != null) {
                    poisonComponent.clear();
                }
                // 删除玩家死后盾
                if (bartenderPlayerComponent != null) {
                    bartenderPlayerComponent.clear();
                }
                var cantSend = SRE.cantSendReplay.stream().anyMatch((pre) -> {
                    return pre.test(serverPlayerEntity);
                });
                if (!cantSend) {
                    serverPlayerEntity.sendSystemMessage(
                            Component.translatable("message.death_reason.prefix", Component.literal("")
                                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                                    .append(deathMessageComponent))
                                    .withStyle(ChatFormatting.DARK_RED));
                }
            } else {
                return;
            }

            // 杀手击杀获得金钱奖励
            if (killer != null && SREGameWorldComponent.KEY.get(killer.level()).canUseKillerFeatures(killer)) {
                int gift = OnGiveKillerBalance.EVENT.invoker().onGiveKillerBalance(victim, killer, deathReason);
                gift += GameConstants.getMoneyPerKill();
                SREPlayerShopComponent.KEY.get(killer).addToBalance(gift);
            }
            if (killer != null) {
                inventory_label: for (List<ItemStack> list : killer.getInventory().compartments) {
                    for (int i = 0; i < list.size(); i++) {
                        ItemStack stack = list.get(i);
                        if (stack.is(TMMItems.DERRINGER)) {
                            stack.set(SREDataComponentTypes.USED, false);
                            break inventory_label;
                        }
                    }
                }
            }

            SREPlayerMoodComponent.KEY.get(victim).init();

            for (List<ItemStack> list : victim.getInventory().compartments) {
                for (int i = 0; i < list.size(); i++) {
                    ItemStack stack = list.get(i);
                    if (shouldDropOnDeath(stack)) {
                        victim.drop(stack, true, false);
                        list.set(i, ItemStack.EMPTY);
                    }
                }
            }

            if (gameWorldComponent.isInnocent(victim)) {
                final var gameTimeComponent = SREGameTimeComponent.KEY.get(victim.level());

                if (gameTimeComponent != null) {
                    {
                        if (gameTimeComponent.getTime() < 60 * 10 * 20) {
                            gameTimeComponent.addTime(GameConstants.TIME_ON_CIVILIAN_KILL);
                        }
                    }
                }
            }
            if (!TrainVoicePlugin.isVoiceChatMissing()) {
                TrainVoicePlugin.addPlayer(victim.getUUID());
            }
        }
    }

    public static boolean shouldDropOnDeath(@NotNull ItemStack stack) {
        return !stack.isEmpty() && (stack.is(TMMItems.REVOLVER) || stack.is(Items.SPYGLASS)
                || ShouldDropOnDeath.EVENT.invoker().shouldDrop(stack));
    }

    public static boolean isPlayerSpectator(Player p) {
        if (p == null)
            return false;
        if (isPlayerSplitPersonalityAndSurvive(p) == SPAliveResult.ALIVE)
            return false;
        return p.isSpectator();
    }

    public static boolean isPlayerAliveAndSurvivalIgnoreShitSplit(Player player) {
        return player != null && !player.isSpectator() && !player.isCreative();
    }

    public static boolean isPlayerAliveAndSurvival(Player player, WorldModifierComponent worldModifierComponent) {
        if (player == null)
            return false;
        if (isPlayerSplitPersonalityAndSurvive(player, worldModifierComponent) == SPAliveResult.ALIVE)
            return true;
        return isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }

    public static boolean isPlayerAliveAndSurvival(Player player) {
        if (player == null)
            return false;
        var worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
        return isPlayerAliveAndSurvival(player, worldModifierComponent);
    }

    public static boolean isPlayerCreative(Player player) {
        return player != null && player.isCreative();
    }

    public static boolean isPlayerSpectatingOrCreative(Player player) {
        if (player == null)
            return false;
        if (isPlayerSplitPersonalityAndSurvive(player) == SPAliveResult.ALIVE)
            return false;
        return isPlayerSpectatingOrCreativeIgnoreShitSplit(player);
    }

    public static enum SPAliveResult {
        ALIVE, DEAD, NOT
    }

    public static SPAliveResult isPlayerSplitPersonalityAndSurvive(Player player) {
        if (player == null)
            return SPAliveResult.DEAD;
        var worldModifierComponent = WorldModifierComponent.KEY.get(player.level());
        return isPlayerSplitPersonalityAndSurvive(player, worldModifierComponent);
    }

    public static SPAliveResult isPlayerSplitPersonalityAndSurvive(Player player,
            WorldModifierComponent worldModifierComponent) {
        if (player == null)
            return SPAliveResult.DEAD;
        if (worldModifierComponent.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
            if (player.isSpectator()) {
                if (!SplitPersonalityComponent.KEY.get(player).isDeath()) {
                    return SPAliveResult.ALIVE;
                } else {
                    return SPAliveResult.DEAD;
                }
            } else if (player.isCreative()) {
                return SPAliveResult.DEAD;
            } else {
                return SPAliveResult.ALIVE;
            }
        }
        return SPAliveResult.NOT;
    }

    public static boolean isPlayerSpectatingOrCreativeIgnoreShitSplit(Player player) {
        return player != null && (player.isSpectator() || player.isCreative());
    }

    record BlockEntityInfo(CompoundTag nbt, DataComponentMap components) {
    }

    record BlockInfo(BlockPos pos, BlockState state, @Nullable BlockEntityInfo blockEntityInfo) {
    }

    enum Mode {
        FORCE(true),
        MOVE(true),
        NORMAL(false);

        private final boolean allowsOverlap;

        Mode(final boolean allowsOverlap) {
            this.allowsOverlap = allowsOverlap;
        }

        public boolean allowsOverlap() {
            return this.allowsOverlap;
        }
    }

    // returns whether another reset should be attempted

    public static boolean tryResetTrain(ServerLevel serverWorld) {
        if (SRE.isLobby)
            return false;
        if (!SREGameWorldComponent.KEY.get(serverWorld).isRunning())
            return false;
        if (SREConfig.instance().enableAutoTrainReset) {
            return tryAutoTrainReset(serverWorld);
        } else {
            return tryResetTrainOnlySomeBlock(serverWorld);
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean tryAutoTrainReset(ServerLevel serverWorld) {
        if (SRE.isLobby)
            return false;
        if (serverWorld.getServer().overworld().equals(serverWorld)) {
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
            if (areas.noReset)
                return false;
            if (SREConfig.instance().verboseTrainResetLogs) {
                SRE.LOGGER.info("Resetting train" + areas.toString());
            }
            BlockPos backupMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
            BlockPos backupMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
            BoundingBox backupTrainBox = BoundingBox.fromCorners(backupMinPos, backupMaxPos);
            BlockPos trainMinPos = BlockPos.containing(areas.getResetPasteArea().getMinPosition());
            BlockPos trainMaxPos = trainMinPos.offset(backupTrainBox.getLength());
            BoundingBox trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);

            // Mode mode = Mode.FORCE;

            if (!serverWorld.hasChunksAt(backupMinPos, backupMaxPos)
                    || !serverWorld.hasChunksAt(trainMinPos, trainMaxPos)) {

                int backupChunkMinX = backupMinPos.getX() >> 4;
                int backupChunkMinZ = backupMinPos.getZ() >> 4;
                int backupChunkMaxX = backupMaxPos.getX() >> 4;
                int backupChunkMaxZ = backupMaxPos.getZ() >> 4;
                int trainChunkMinX = trainMinPos.getX() >> 4;
                int trainChunkMinZ = trainMinPos.getZ() >> 4;
                int trainChunkMaxX = trainMaxPos.getX() >> 4;
                int trainChunkMaxZ = trainMaxPos.getZ() >> 4;

                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info(
                            "Train reset: Loading chunks - Template: ({}, {}) to ({}, {}), Paste: ({}, {}) to ({}, {})",
                            backupChunkMinX, backupChunkMinZ, backupChunkMaxX, backupChunkMaxZ,
                            trainChunkMinX, trainChunkMinZ, trainChunkMaxX, trainChunkMaxZ);
                }

                // Force load the required chunks
                for (int x = backupChunkMinX; x <= backupChunkMaxX; x++) {
                    for (int z = backupChunkMinZ; z <= backupChunkMaxZ; z++) {
                        serverWorld.getChunk(x, z);
                    }
                }
                for (int x = trainChunkMinX; x <= trainChunkMaxX; x++) {
                    for (int z = trainChunkMinZ; z <= trainChunkMaxZ; z++) {
                        serverWorld.getChunk(x, z);
                    }
                }

                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("Train reset: Chunks loaded, attempting reset.");
                }
                // Continue with the reset after loading chunks
            }

            if (serverWorld.hasChunksAt(backupMinPos, backupMaxPos)
                    && serverWorld.hasChunksAt(trainMinPos, trainMaxPos)) {
                List<BlockInfo> list = Lists.newArrayList();
                List<BlockInfo> list2 = Lists.newArrayList();
                List<BlockInfo> list3 = Lists.newArrayList();
                Deque<BlockPos> deque = Lists.newLinkedList();
                BlockPos blockPos5 = new BlockPos(
                        trainBox.minX() - backupTrainBox.minX(), trainBox.minY() - backupTrainBox.minY(),
                        trainBox.minZ() - backupTrainBox.minZ());

                for (int k = backupTrainBox.minZ(); k <= backupTrainBox.maxZ(); k++) {
                    for (int l = backupTrainBox.minY(); l <= backupTrainBox.maxY(); l++) {
                        for (int m = backupTrainBox.minX(); m <= backupTrainBox.maxX(); m++) {
                            BlockPos blockPos6 = new BlockPos(m, l, k);
                            BlockPos blockPos7 = blockPos6.offset(blockPos5);
                            BlockInWorld cachedBlockPosition = new BlockInWorld(serverWorld, blockPos6, false);
                            BlockState blockState = cachedBlockPosition.getState();

                            BlockEntity blockEntity = serverWorld.getBlockEntity(blockPos6);
                            if (blockEntity != null) {
                                BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                        blockEntity.saveCustomOnly(serverWorld.registryAccess()),
                                        blockEntity.components());
                                list2.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                deque.addLast(blockPos6);
                            } else if (!blockState.isSolidRender(serverWorld, blockPos6)
                                    && !blockState.isCollisionShapeFullBlock(serverWorld, blockPos6)) {
                                list3.add(new BlockInfo(blockPos7, blockState, null));
                                deque.addFirst(blockPos6);
                            } else {
                                list.add(new BlockInfo(blockPos7, blockState, null));
                                deque.addLast(blockPos6);
                            }
                        }
                    }
                }

                List<BlockInfo> list4 = Lists.newArrayList();
                list4.addAll(list);
                list4.addAll(list2);
                list4.addAll(list3);
                List<BlockInfo> list5 = Lists.reverse(list4);

                for (BlockInfo blockInfo : list5) {
                    BlockEntity blockEntity3 = serverWorld.getBlockEntity(blockInfo.pos);
                    Clearable.tryClear(blockEntity3);
                    serverWorld.setBlock(blockInfo.pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }

                int mx = 0;

                for (BlockInfo blockInfo2 : list4) {
                    if (serverWorld.setBlock(blockInfo2.pos, blockInfo2.state, Block.UPDATE_CLIENTS)) {
                        mx++;
                    }
                }

                for (BlockInfo blockInfo2x : list2) {
                    BlockEntity blockEntity4 = serverWorld.getBlockEntity(blockInfo2x.pos);
                    if (blockInfo2x.blockEntityInfo != null && blockEntity4 != null) {
                        blockEntity4.loadCustomOnly(blockInfo2x.blockEntityInfo.nbt, serverWorld.registryAccess());
                        blockEntity4.setComponents(blockInfo2x.blockEntityInfo.components);
                        blockEntity4.setChanged();
                    }

                    serverWorld.setBlock(blockInfo2x.pos, blockInfo2x.state, Block.UPDATE_CLIENTS);
                }

                for (BlockInfo blockInfo2x : list5) {
                    serverWorld.blockUpdated(blockInfo2x.pos, blockInfo2x.state.getBlock());
                }

                serverWorld.getBlockTicks().copyAreaFrom(serverWorld.getBlockTicks(), backupTrainBox, blockPos5);
                if (mx == 0) {
                    if (SREConfig.instance().verboseTrainResetLogs) {
                        SRE.LOGGER.info("Train reset failed: No blocks copied. Queueing another attempt.");
                    }
                    // return true;
                }
            } else {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("Train reset failed: Clone positions not loaded. Queueing another attempt.");
                }
                return true;
            }

            // discard all player bodies and items
            resetEntities(serverWorld);

            SRE.LOGGER.info("Train reset successful.");
            return false;
        }
        return false;
    }

    private static void resetEntities(ServerLevel serverWorld) {
        for (PlayerBodyEntity body : serverWorld.getEntities(TMMEntities.PLAYER_BODY,
                playerBodyEntity -> true)) {
            body.discard();
        }
        for (ItemEntity item : serverWorld.getEntities(EntityType.ITEM, playerBodyEntity -> true)) {
            item.discard();
        }
        for (FirecrackerEntity entity : serverWorld.getEntities(TMMEntities.FIRECRACKER, entity -> true))
            entity.discard();
        for (NoteEntity entity : serverWorld.getEntities(TMMEntities.NOTE, entity -> true))
            entity.discard();
        EntityClearUtils.clearAllEntities(serverWorld);
        SRE.LOGGER.info("Kill all related entities in game world!");
    }

    @SuppressWarnings("deprecation")
    public static boolean tryResetTrainOnlySomeBlock(ServerLevel serverWorld) {
        if (SRE.isLobby)
            return false;
        if (serverWorld.getServer().overworld().equals(serverWorld)) {
            AreasWorldComponent areas = AreasWorldComponent.KEY.get(serverWorld);
            if (areas.noReset)
                return false;
            if (SREConfig.instance().verboseTrainResetLogs) {
                SRE.LOGGER.info("Resetting train" + areas.toString());
            }
            BlockPos backupMinPos = BlockPos.containing(areas.getResetTemplateArea().getMinPosition());
            BlockPos backupMaxPos = BlockPos.containing(areas.getResetTemplateArea().getMaxPosition());
            BoundingBox backupTrainBox = BoundingBox.fromCorners(backupMinPos, backupMaxPos);
            BlockPos trainMinPos = BlockPos.containing(areas.getResetPasteArea().getMinPosition());
            BlockPos trainMaxPos = trainMinPos.offset(backupTrainBox.getLength());
            BoundingBox trainBox = BoundingBox.fromCorners(trainMinPos, trainMaxPos);

            // Mode mode = Mode.FORCE;

            if (!serverWorld.hasChunksAt(backupMinPos, backupMaxPos)
                    || !serverWorld.hasChunksAt(trainMinPos, trainMaxPos)) {

                int backupChunkMinX = backupMinPos.getX() >> 4;
                int backupChunkMinZ = backupMinPos.getZ() >> 4;
                int backupChunkMaxX = backupMaxPos.getX() >> 4;
                int backupChunkMaxZ = backupMaxPos.getZ() >> 4;
                int trainChunkMinX = trainMinPos.getX() >> 4;
                int trainChunkMinZ = trainMinPos.getZ() >> 4;
                int trainChunkMaxX = trainMaxPos.getX() >> 4;
                int trainChunkMaxZ = trainMaxPos.getZ() >> 4;

                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info(
                            "Train reset: Loading chunks - Template: ({}, {}) to ({}, {}), Paste: ({}, {}) to ({}, {})",
                            backupChunkMinX, backupChunkMinZ, backupChunkMaxX, backupChunkMaxZ,
                            trainChunkMinX, trainChunkMinZ, trainChunkMaxX, trainChunkMaxZ);
                }

                // Force load the required chunks
                // for (int x = backupChunkMinX; x <= backupChunkMaxX; x++) {
                // for (int z = backupChunkMinZ; z <= backupChunkMaxZ; z++) {
                // // serverWorld.getChunk(x, z);
                // }
                // }
                for (int x = trainChunkMinX; x <= trainChunkMaxX; x++) {
                    for (int z = trainChunkMinZ; z <= trainChunkMaxZ; z++) {
                        serverWorld.getChunk(x, z);
                    }
                }

                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("Train reset: Chunks loaded, attempting reset.");
                }
                // Continue with the reset after loading chunks
            }

            if (serverWorld.hasChunksAt(trainMinPos, trainMaxPos)) {
                List<BlockInfo> list3 = Lists.newArrayList(); // 仅更新方块状态

                List<BlockInfo> list2 = Lists.newArrayList(); // Only store block entities (doors have block entities)
                // Deque<BlockPos> deque = Lists.newLinkedList();

                // Only clone door blocks and their entities
                for (int k = trainBox.minZ(); k <= trainBox.maxZ(); k++) {
                    for (int l = trainBox.minY(); l <= trainBox.maxY(); l++) {
                        for (int m = trainBox.minX(); m <= trainBox.maxX(); m++) {
                            BlockPos blockPos6 = new BlockPos(m, l, k);
                            BlockPos blockPos7 = blockPos6;
                            BlockInWorld cachedBlockPosition = new BlockInWorld(serverWorld, blockPos6, false);
                            BlockState blockState = cachedBlockPosition.getState();

                            // Check if the block is one of our door blocks
                            if (blockState.getBlock() instanceof SmallDoorBlock) {
                                if (blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.LOWER)) {
                                    if (serverWorld.getBlockEntity(blockPos6) instanceof SmallDoorBlockEntity entity) {
                                        entity.setBlasted(false);
                                        entity.setJammed(0);
                                        entity.setOpen(false);
                                        String keyName = entity.getKeyName();
                                        if (keyName == null)
                                            keyName = "";
                                        else if (keyName.endsWith(":")) {
                                            keyName = "";
                                        } else if (keyName.contains(":")) {
                                            var arr = keyName.split(":");
                                            if (arr.length > 0) {
                                                keyName = arr[arr.length - 1];
                                            }
                                        }
                                        entity.setKeyName(keyName);
                                        blockState = blockState.setValue(SmallDoorBlock.OPEN, false);
                                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                                entity.components());
                                        list2.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                    }
                                } else if (blockState.getValue(SmallDoorBlock.HALF).equals(DoubleBlockHalf.UPPER)) {
                                    blockState = blockState.setValue(SmallDoorBlock.OPEN, false);
                                    list2.add(new BlockInfo(blockPos7, blockState, null));
                                }
                            } else if (blockState.getBlock() instanceof TrimmedBedBlock) {
                                if (blockState.getValue(TrimmedBedBlock.PART).equals(BedPart.HEAD)) {
                                    if (serverWorld.getBlockEntity(blockPos6) instanceof TrimmedBedBlockEntity entity) {
                                        entity.setHasScorpion(false, null);
                                        blockState = blockState.setValue(TrimmedBedBlock.OCCUPIED, false);
                                        BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                                entity.saveCustomOnly(serverWorld.registryAccess()),
                                                entity.components());
                                        list3.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                        // deque.addLast(blockPos6); // Add to end to process last
                                    }
                                }
                            } else if (blockState.getBlock() instanceof FoodPlatterBlock) {
                                if (serverWorld.getBlockEntity(blockPos6) instanceof BeveragePlateBlockEntity entity) {
                                    entity.setArmorer(null);
                                    entity.setPoisoner(null);
                                    BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                            entity.saveCustomOnly(serverWorld.registryAccess()),
                                            entity.components());
                                    list3.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                }
                            } else if (blockState.getBlock() instanceof LecternBlock) {
                                if (serverWorld.getBlockEntity(blockPos6) instanceof LecternBlockEntity entity) {
                                    BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                            entity.saveCustomOnly(serverWorld.registryAccess()),
                                            entity.components());
                                    list3.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                }
                            } else if (blockState.getBlock() instanceof SprinklerBlock) {
                                if (serverWorld.getBlockEntity(blockPos6) instanceof SprinklerBlockEntity entity) {
                                    entity.setPowered(false);
                                    BlockEntityInfo blockEntityInfo = new BlockEntityInfo(
                                            entity.saveCustomOnly(serverWorld.registryAccess()),
                                            entity.components());
                                    blockState = blockState.setValue(SprinklerBlock.POWERED, false);
                                    list3.add(new BlockInfo(blockPos7, blockState, blockEntityInfo));
                                }
                            } else if (blockState.getBlock() instanceof NeonPillarBlock) {
                                blockState = blockState.setValue(NeonPillarBlock.ACTIVE, true);
                                blockState = blockState.setValue(NeonPillarBlock.LIT, true);
                                list2.add(new BlockInfo(blockPos7, blockState, null));
                            } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                                blockState = blockState.setValue(NeonTubeBlock.ACTIVE, true);
                                blockState = blockState.setValue(NeonTubeBlock.LIT, true);
                                list2.add(new BlockInfo(blockPos7, blockState, null));
                            } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                                blockState = blockState.setValue(NeonTubeBlock.ACTIVE, true);
                                blockState = blockState.setValue(NeonTubeBlock.LIT, true);
                                list2.add(new BlockInfo(blockPos7, blockState, null));
                            } else if (blockState.getBlock() instanceof ToggleableFacingLightBlock) {
                                blockState = blockState.setValue(ToggleableFacingLightBlock.ACTIVE, true);
                                blockState = blockState.setValue(ToggleableFacingLightBlock.LIT, true);
                                list2.add(new BlockInfo(blockPos7, blockState, null));
                            } else if (blockState.getBlock() instanceof VentHatchBlock) {
                                blockState = blockState.setValue(VentHatchBlock.OPEN, false);
                                list2.add(new BlockInfo(blockPos7, blockState, null));
                            }
                        }
                    }
                }

                List<BlockInfo> list4 = Lists.newArrayList();
                list4.addAll(list2); // Only doors
                List<BlockInfo> list_onlyBlockEntity = Lists.newArrayList();
                list_onlyBlockEntity.addAll(list2);
                list_onlyBlockEntity.addAll(list3);
                List<BlockInfo> list6 = Lists.newArrayList();
                list6.addAll(list4);
                list6.addAll(list3);
                List<BlockInfo> list5 = Lists.reverse(list6);

                // Clear only the door locations with barrier blocks
                for (BlockInfo blockInfo : list5) {
                    BlockEntity blockEntity3 = serverWorld.getBlockEntity(blockInfo.pos);
                    Clearable.tryClear(blockEntity3);
                }
                for (BlockInfo blockInfo : list4) {
                    serverWorld.setBlock(blockInfo.pos, Blocks.BARRIER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }

                @SuppressWarnings("unused")
                int mx = 1;

                // Place the doors back
                for (BlockInfo blockInfo2 : list4) {
                    if (serverWorld.setBlock(blockInfo2.pos, blockInfo2.state, Block.UPDATE_CLIENTS)) {
                        mx++;
                    }
                }

                // Restore block entities for doors
                for (BlockInfo blockInfo2x : list_onlyBlockEntity) {
                    BlockEntity blockEntity4 = serverWorld.getBlockEntity(blockInfo2x.pos);
                    if (blockInfo2x.blockEntityInfo != null && blockEntity4 != null) {
                        blockEntity4.loadCustomOnly(blockInfo2x.blockEntityInfo.nbt, serverWorld.registryAccess());
                        blockEntity4.setComponents(blockInfo2x.blockEntityInfo.components);
                        blockEntity4.setChanged();
                    }

                }
                // for (BlockInfo blockInfo2x : list4) {
                // serverWorld.setBlock(blockInfo2x.pos, blockInfo2x.state,
                // Block.UPDATE_CLIENTS);
                // }

                // Update the blocks after setting them
                for (BlockInfo blockInfo2x : list5) {
                    serverWorld.blockUpdated(blockInfo2x.pos, blockInfo2x.state.getBlock());
                }

                // if (mx == 0) {
                // if (TMMConfig.verboseTrainResetLogs) {
                // }
                // return true;
                // }
            } else {
                if (SREConfig.instance().verboseTrainResetLogs) {
                    SRE.LOGGER.info("Train door reset failed: Clone positions not loaded.");
                }
                return false;
            }

            // Discard all player bodies and items (keep this part as it cleans up game
            // artifacts)
            for (PlayerBodyEntity body : serverWorld.getEntities(TMMEntities.PLAYER_BODY, playerBodyEntity -> true)) {
                body.discard();
            }
            for (ItemEntity item : serverWorld.getEntities(EntityType.ITEM, playerBodyEntity -> true)) {
                item.discard();
            }
            for (FirecrackerEntity entity : serverWorld.getEntities(TMMEntities.FIRECRACKER, entity -> true))
                entity.discard();
            for (NoteEntity entity : serverWorld.getEntities(TMMEntities.NOTE, entity -> true))
                entity.discard();

            // Reset all toilet block entities
            resetAllToilets(serverWorld);

            SRE.LOGGER.info("Train door reset successful.");
            return false;
        }
        return false;
    }

    /**
     * Checks if a block is one of TMM's door blocks
     */
    public static boolean isTmmDoorBlock(Block block) {
        return block == TMMBlocks.SMALL_GLASS_DOOR
                || block == TMMBlocks.SMALL_WOOD_DOOR
                || block == TMMBlocks.ANTHRACITE_STEEL_DOOR
                || block == TMMBlocks.KHAKI_STEEL_DOOR
                || block == TMMBlocks.MAROON_STEEL_DOOR
                || block == TMMBlocks.MUNTZ_STEEL_DOOR
                || block == TMMBlocks.NAVY_STEEL_DOOR
                || block == TMMBlocks.METAL_SHEET_DOOR
                || block == TMMBlocks.COCKPIT_DOOR;
    }

    public static int getReadyPlayerCount(Level world) {
        List<? extends Player> players = world.players();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        return Math.toIntExact(players.stream().filter(p -> areas.getReadyArea().contains(p.position())).count());
    }

    public enum WinStatus {
        NOT_MODIFY, NONE, KILLERS, PASSENGERS, TIME, LOOSE_END, GAMBLER, RECORDER, NO_PLAYER, NIAN_SHOU, LOVERS,
        CUSTOM_COMPONENT, CUSTOM
    }
}