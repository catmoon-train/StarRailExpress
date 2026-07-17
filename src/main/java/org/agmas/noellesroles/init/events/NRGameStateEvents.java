package org.agmas.noellesroles.init.events;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.*;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.network.RemoveStatusBarPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.*;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.HallucinationAreaManager;
import org.agmas.noellesroles.content.entity.ServerSmokeAreaManager;
import org.agmas.noellesroles.content.item.*;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.TarotAssemblyManager;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinFistPunchHandler;
import org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanManager;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.init.ModEventsRegister;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.init.RoleShopHandler;
import org.agmas.noellesroles.packet.BloodConfigS2CPacket;
import org.agmas.noellesroles.packet.EmbalmerSkinSwapS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.*;

/**
 * 游戏生命周期、服务器 Tick、玩家连接事件处理
 */
public class NRGameStateEvents {

    private static AttributeModifier noJumpingAttribute = new AttributeModifier(
            Noellesroles.id("no_jumping"), -1.0f, AttributeModifier.Operation.ADD_VALUE);
    private static final Map<UUID, Vec3> oldmanPigRidePositions = new HashMap<>();

    /** 本局游戏是否已发放过年兽鞭炮（一局只能有一次） */
    public static boolean nianShouFirecrackersDistributedThisGame = false;

    public static void register() {
        registerOnGameStarted();
        registerOnGameEnd();
        registerOnGameTrueStarted();
        registerServerLifecycle();
        registerServerTick();
        registerPlayerConnection();
    }

    // --- OnGameStarted ---

    private static void registerOnGameStarted() {
        OnGameStarted.EVENT.register((serverLevel) -> {
            TarotAssemblyManager.havingMeeting = false;
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();

            // 清除所有玩家的感染状态
            for (ServerPlayer player : serverLevel.players()) {
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent != null) {
                    infectedComponent.cure();
                }
            }
            // 重置疫使时刻状态
            org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.resetAcceleratedState();

            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverLevel);
            final var all_players = serverLevel.players();
            for (var p : all_players) {
                if (!gameWorldComponent.isJumpAvailable() && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                    if (!p.hasPermissions(2)) {
                        p.getAttribute(Attributes.JUMP_STRENGTH).addOrReplacePermanentModifier(noJumpingAttribute);
                    }
                }
                if (worldModifierComponent.isModifier(p, NRModifiers.EXPEDITION)) {
                    SRERole role = gameWorldComponent.getRole(p);
                    var expeditionComponent = ExpeditionComponent.KEY.get(p);
                    if (expeditionComponent != null && expeditionComponent.isExpedition()) {
                        if (role != null && (!role.isInnocent() || role.canUseKiller() || role.isNeutrals())) {
                            expeditionComponent.clear();
                            expeditionComponent.sync();
                            worldModifierComponent.removeModifier(p.getUUID(), NRModifiers.EXPEDITION);
                            Noellesroles.LOGGER
                                    .info("Expedition modifier effect disabled for player due to role change: "
                                            + p.getName().getString() + ", new role: " + role.identifier());
                        }
                    }
                }
            }
        });
    }

    // --- OnGameEnd ---

    private static void registerOnGameEnd() {
        OnGameEnd.EVENT.register((world, gameWorldComponent) -> {
            nianShouFirecrackersDistributedThisGame = false;
            HoanMeirinFistPunchHandler.PUNCH_RECORDS.clear();
            RoleShopHandler.resetOldmanEasterEggState();
            DelayerPlayerComponent.timeBoostTriggered = false;

            // 清除感染状态
            for (ServerPlayer player : world.players()) {
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(player);
                if (infectedComponent != null) {
                    infectedComponent.cure();
                }
            }
            org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker.resetAcceleratedState();

            // 清除建筑师客户端墙
            for (ServerPlayer player : world.players()) {
                var builderComp = ModComponents.BUILDER.get(player);
                builderComp.clearAllWalls();
            }
            org.agmas.noellesroles.game.roles.innocence.builder.BuilderWallPositions.clearAll();

            // 清除冒险家路径点
            io.wifi.starrailexpress.game.data.WaypointVisibilityManager.get(world.getServer())
                    .setWaypointsVisibility(false);

            // 清除鹈鹕状态
            PelicanManager.releaseAllInWorld(world);
            PelicanManager.clearAll();

            // 清除嬉命人变装
            for (ServerPlayer p : world.players()) {
                ServerPlayNetworking.send(p, EmbalmerSkinSwapS2CPacket.clear());
            }

            // 清除所有肉汁的悬赏
            for (ServerPlayer player : world.players()) {
                ModComponents.MEATBALL.get(player).init();
            }

            // 重置实体交互方块内置冷却
            if (io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity.getCountForMap(world) > 0) {
                var playArea = AreasWorldComponent.KEY.get(world).getPlayArea();
                int minChunkX = ((int) playArea.minX) >> 4;
                int maxChunkX = ((int) playArea.maxX) >> 4;
                int minChunkZ = ((int) playArea.minZ) >> 4;
                int maxChunkZ = ((int) playArea.maxZ) >> 4;
                var chunkSource = world.getChunkSource();
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        var chunk = chunkSource.getChunkNow(cx, cz);
                        if (chunk != null) {
                            for (var be : chunk.getBlockEntities().values()) {
                                if (be instanceof io.wifi.starrailexpress.content.block_entity.EntityInteractionBlockEntity entity
                                        && playArea.contains(entity.getBlockPos().getCenter())) {
                                    entity.resetAllCooldowns();
                                }
                            }
                        }
                    }
                }
            }

            // 时间到时的特殊处理
            SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(world);
            if (roundEnd.getWinStatus().equals(GameUtils.WinStatus.TIME)) {
                int alivePlayers = 0, aliveKillers = 0, aliveGhost = 0;
                var players = world.players();
                for (ServerPlayer player : players) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        alivePlayers++;
                        if (gameWorldComponent.isKillerTeam(player)) {
                            aliveKillers++;
                        }
                        if (gameWorldComponent.isRole(player, ModRoles.GHOST)) {
                            aliveGhost++;
                        }
                    }
                }
                if (aliveGhost >= 1 && aliveKillers >= 1 && aliveGhost + aliveKillers >= alivePlayers) {
                    GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(8 * 20, () -> {
                        players.forEach((p) -> {
                            p.playNotifySound(NRSounds.TO_BE_CONTINUED, SoundSource.MASTER, 0.5f, 1f);
                        });
                    }));
                }
            }
        });
    }

    // --- OnGameTrueStarted ---

    private static void registerOnGameTrueStarted() {
        OnGameTrueStarted.EVENT.register((serverLevel) -> {
            SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(serverLevel);
            boolean hasDio = false, hasRecorder = false, hasCandlebearer = false, hasRaven = false;
            boolean hasNianShou = false, hasArsonist = false, hasCuckoo = false, hasPelican = false, hasGodfather = false;
            final var all_players = serverLevel.players();

            for (var p : all_players) {
                if (!gameWorldComponent.isJumpAvailable() && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(p)) {
                    if (!p.hasPermissions(2)) {
                        p.getAttribute(Attributes.JUMP_STRENGTH).addOrReplacePermanentModifier(noJumpingAttribute);
                    }
                }

                if (gameWorldComponent.isRole(p, ModRoles.THIEF)) {
                    ThiefPlayerComponent.KEY.get(p).updateHonorCost(serverLevel.players().size());
                } else if (gameWorldComponent.isRole(p, ModRoles.ATTENDANT)) {
                    SRE.SendRoomInfoToPlayer(p);
                } else if (gameWorldComponent.isRole(p, ModRoles.DIO)) {
                    hasDio = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.RECORDER)) {
                    hasRecorder = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.CANDLE_BEARER)) {
                    hasCandlebearer = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.RAVEN)) {
                    hasRaven = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.NIAN_SHOU)) {
                    hasNianShou = true;
                } else if (gameWorldComponent.isRole(p, SERoles.ARSONIST)) {
                    hasArsonist = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.CUCKOO)) {
                    hasCuckoo = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.PELICAN)) {
                    hasPelican = true;
                } else if (gameWorldComponent.isRole(p, ModRoles.GODFATHER)) {
                    hasGodfather = true;
                }
            }

            if (hasDio) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(20 * 8, () -> {
                    all_players.forEach((p) -> {
                        if (p != null)
                            p.playNotifySound(NRSounds.DIO_SPAWN, SoundSource.PLAYERS, 0.5F, 1.0F);
                    });
                }));
            }
            if (hasRecorder) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.recorder.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasCandlebearer) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.candlebearer.entry")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasRaven) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.raven.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasArsonist) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.arsonist.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasCuckoo) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.cuckoo.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasPelican) {
                all_players.forEach((p) -> {
                    if (p != null) {
                        BroadcastCommand.BroadcastMessage(p, Component
                                .translatable("message.noellesroles.pelican.entry").withStyle(ChatFormatting.YELLOW));
                    }
                });
            }
            if (hasGodfather) {
                GameUtils.serverAsynTaskLists.add(new ServerTaskInfoClasses.SchedulerTask(20 * 6, () -> {
                    all_players.forEach((p) -> {
                        if (p != null)
                            p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
                    });
                }));
            }
            if (hasNianShou && !nianShouFirecrackersDistributedThisGame) {
                nianShouFirecrackersDistributedThisGame = true;
                for (var player : all_players) {
                    ItemStack firecrackerStack = new ItemStack(TMMItems.FIRECRACKER);
                    firecrackerStack.set(DataComponents.MAX_STACK_SIZE, 4);
                    firecrackerStack.setCount(4);
                    player.getInventory().add(firecrackerStack);
                    BroadcastCommand.BroadcastMessage(player, Component
                            .translatable("message.noellesroles.nianshou.firecrackers_distributed")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
        });
    }

    // --- ServerLifecycleEvents ---

    private static void registerServerLifecycle() {
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            if (ModEventsRegister.isMJVerifyEnabled) {
                Harpymodloader.officialVerify = Noellesroles.checkMJVerify();
            } else {
                Harpymodloader.officialVerify = true;
            }
        });
    }

    // --- ServerTick ---

    private static void registerServerTick() {
        // 烟雾/迷幻区域 + 塔罗 + 收音机
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            ServerSmokeAreaManager.tick();
            net.exmo.sre.sixtyseconds.content.entity.ServerMarkingAreaManager.tick();
            org.agmas.noellesroles.content.entity.ServerFireAreaManager.tick();
            HallucinationAreaManager.tick();
            ServerLevel level = server.overworld();
            TarotAssemblyManager.serverLevelTick(level);

            if (server.getTickCount() % 10 == 0) {
                HashSet<UUID> toDeleted = new HashSet<>();
                for (var p_u : RadioItem.CHANNELS.keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(p_u);
                    if (p == null) {
                        toDeleted.add(p_u);
                    } else {
                        if (p.isSpectator()) {
                            toDeleted.add(p_u);
                            p.displayClientMessage(Component.translatable("message.noellesroles.radio.left")
                                    .withStyle(ChatFormatting.RED), true);
                        } else if (!MCItemsUtils.hasItem(p, ModItems.RADIO)) {
                            toDeleted.add(p_u);
                            p.displayClientMessage(Component.translatable("message.noellesroles.radio.left")
                                    .withStyle(ChatFormatting.RED), true);
                        }
                    }
                }
                RadioItem.CHANNELS.keySet().removeAll(toDeleted);
            }
        });

        // 老人猪处理
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var gameWorldComponent = SREGameWorldComponent.KEY.maybeGet(server.overworld()).orElse(null);
            if (gameWorldComponent == null || !gameWorldComponent.isRunning())
                return;
            ServerLevel level = server.overworld();
            List<? extends Pig> pigs = level.getEntities(EntityTypeTest.forExactClass(Pig.class),
                    (pig) -> pig.getTags().contains(RoleShopHandler.OLDMAN_EASTER_EGG_PIG_NO_STEP_TAG));
            for (Pig pig : pigs) {
                if (pig.getControllingPassenger() == null) {
                    oldmanPigRidePositions.remove(pig.getUUID());
                    continue;
                }
                pig.setJumping(false);
            }
        });

        // 喷气背包 tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                JetpackItem.tickJetpackEffect(player);
            }
        });

        // 时间停止 effect
        ServerTickEvents.START_SERVER_TICK.register((server) -> {
            if (TimeStopEffect.freezeTime > 0) {
                TimeStopEffect.freezeTime--;
                if (TimeStopEffect.freezeTime == 0) {
                    server.getPlayerList().getPlayers().forEach((player) -> {
                        if (TimeStopEffect.canMovePlayers.contains(player.getUUID())) {
                            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 5, 0, false, false, false));
                        }
                        ServerPlayNetworking.send(player, new RemoveStatusBarPayload("Time_Stop"));
                    });
                    server.tickRateManager().setFrozen(false);
                }
            }
        });
    }

    // --- PlayerConnection ---

    private static void registerPlayerConnection() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            final var player = handler.getPlayer();
            NRDeathEvents.handleDeathPenalty(player, true, true);
            sender.sendPacket(new BloodConfigS2CPacket(NoellesRolesConfig.HANDLER.instance().enableClientBlood));
        });
    }

}
