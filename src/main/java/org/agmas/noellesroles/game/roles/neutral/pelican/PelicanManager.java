package org.agmas.noellesroles.game.roles.neutral.pelican;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.voice.NoellesrolesVoiceChatPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PelicanManager {
    private static final Map<UUID, UUID> pelicanByStashed = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<UUID>> stashedByPelican = new ConcurrentHashMap<>();
    private static final Map<UUID, GameType> stashedPreviousGameMode = new ConcurrentHashMap<>();
    // 标记在鹈鹕肚内死亡的玩家，防止后续释放逻辑把他们复活
    private static final Set<UUID> stashedDead = ConcurrentHashMap.newKeySet();
    private static int stashedStateSyncTicker = 0;
    private static final int STASHED_STATE_SYNC_INTERVAL_TICKS = 10;

    private PelicanManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(PelicanManager::tick);
    }

    private static void tick(ServerLevel world) {
        if (world.dimension() != Level.OVERWORLD)
            return;
        if (pelicanByStashed.isEmpty())
            return;

        boolean syncCamera = false;
        if (stashedStateSyncTicker-- <= 0) {
            stashedStateSyncTicker = STASHED_STATE_SYNC_INTERVAL_TICKS;
            syncCamera = true;
        }

        MinecraftServer server = world.getServer();
        for (Map.Entry<UUID, UUID> entry : List.copyOf(pelicanByStashed.entrySet())) {
            UUID targetId = entry.getKey();
            UUID pelicanId = entry.getValue();
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            ServerPlayer pelican = server.getPlayerList().getPlayer(pelicanId);
            if (target == null)
                continue;
            if (pelican == null || !GameUtils.isPlayerAliveAndSurvival(pelican)) {
                releasePlayer(target);
                continue;
            }
            if (target.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                target.setGameMode(GameType.SPECTATOR);
            }
            target.setInvisible(true);
            if (target.level() != pelican.level() || target.distanceToSqr(pelican) > 4.0D) {
                target.teleportTo(pelican.serverLevel(), pelican.getX(), pelican.getY(), pelican.getZ(),
                        pelican.getYRot(), pelican.getXRot());
                syncCamera = true;
            }
            if (syncCamera) {
                target.connection.send(new ClientboundSetCameraPacket(pelican));
            }
        }
    }

    public static void stashPlayer(ServerPlayer pelican, ServerPlayer target) {
        UUID pelicanId = pelican.getUUID();
        UUID targetId = target.getUUID();

        stashedPreviousGameMode.put(targetId, target.gameMode.getGameModeForPlayer());
        pelicanByStashed.put(targetId, pelicanId);
        stashedByPelican.computeIfAbsent(pelicanId, id -> new ArrayDeque<>()).addLast(targetId);
        redirectExecutionerTargetsToPelican(targetId, pelican);

        target.stopRiding();
        target.setShiftKeyDown(false);
        target.setInvisible(true);
        target.setGameMode(GameType.SPECTATOR);
        target.teleportTo(pelican.serverLevel(), pelican.getX(), pelican.getY(), pelican.getZ(),
                pelican.getYRot(), pelican.getXRot());
        target.connection.send(new ClientboundSetCameraPacket(pelican));

        // 被吞噬玩家进入 DeathPenalty，视角锁定在鹈鹕身上；清除可能存在的起搏器保护
        DeathPenaltyComponent.KEY.get(target).setPenaltyWithCameraLimit(-1, pelican, true);
        DefibrillatorComponent.KEY.get(target).init();

        // 添加很长时间的禁用技能、禁用聊天栏和禁用使用物品药水效果
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, Integer.MAX_VALUE, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, Integer.MAX_VALUE, 0, false, false, false));
        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, Integer.MAX_VALUE, 0, false, false, false));

        // 如果目标处于疯狂模式（psycho），立刻结束
        SREPlayerPsychoComponent psychoComp = SREPlayerPsychoComponent.KEY.get(target);
        if (psychoComp.getPsychoTicks() > 0) {
            psychoComp.stopPsychoAndSync();
        }

        // 如果目标中毒，立刻治愈
        SREPlayerPoisonComponent poisonComp = SREPlayerPoisonComponent.KEY.get(target);
        if (poisonComp.poisonTicks > 0) {
            poisonComp.init();
        }

        // 如果目标被感染，立刻治愈
        InfectedPlayerComponent infectedComp = ModComponents.INFECTED.get(target);
        if (infectedComp.infectedTicks > 0) {
            infectedComp.cure();
        }

        // 如果疫使被鹈鹕吃了，治愈场上所有玩家的感染状态
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(target.level());
        if (gameWorld != null && gameWorld.isRole(target, ModRoles.INFECTED)) {
            for (ServerPlayer p : target.serverLevel().players()) {
                InfectedPlayerComponent otherInfected = ModComponents.INFECTED.get(p);
                if (otherInfected.infectedTicks > 0) {
                    otherInfected.cure();
                }
            }
        }

        NoellesrolesVoiceChatPlugin.onPelicanStash(targetId, pelicanId);
    }

    private static void redirectExecutionerTargetsToPelican(UUID stashedTargetId, ServerPlayer pelican) {
        SREGameWorldComponent pelicanWorld = SREGameWorldComponent.KEY.get(pelican.level());
        if (pelicanWorld == null
                || !pelicanWorld.isRole(pelican, ModRoles.PELICAN)
                || !GameUtils.isPlayerAliveAndSurvival(pelican)
                || PelicanManager.isStashed(pelican)) {
            return;
        }
        for (ServerPlayer candidate : pelican.getServer().getPlayerList().getPlayers()) {
            SREGameWorldComponent candidateWorld = SREGameWorldComponent.KEY.get(candidate.level());
            if (candidateWorld == null || !candidateWorld.isRole(candidate, ModRoles.EXECUTIONER)) {
                continue;
            }
            ExecutionerPlayerComponent executioner = ExecutionerPlayerComponent.KEY.get(candidate);
            if (stashedTargetId.equals(executioner.target)) {
                executioner.target = pelican.getUUID();
                executioner.targetSelected = true;
                executioner.sync();
            }
        }
    }

    public static void releasePlayer(ServerPlayer target) {
        UUID targetId = target.getUUID();
        UUID pelicanId = pelicanByStashed.remove(targetId);
        if (pelicanId != null) {
            Deque<UUID> belly = stashedByPelican.get(pelicanId);
            if (belly != null) {
                belly.remove(targetId);
                if (belly.isEmpty())
                    stashedByPelican.remove(pelicanId);
            }
        }
        GameType restoreMode = stashedPreviousGameMode.getOrDefault(targetId, GameType.ADVENTURE);
        stashedPreviousGameMode.remove(targetId);

        // 恢复聊天并清除 DeathPenalty/起搏器
        DeathPenaltyComponent.KEY.get(target).init();
        DefibrillatorComponent.KEY.get(target).init();

        // 清除被吞噬时添加的禁用技能、禁用聊天栏和禁用使用物品效果
        target.removeEffect(ModEffects.SKILL_BANED);
        target.removeEffect(ModEffects.CHAT_BAN);
        target.removeEffect(ModEffects.USED_BANED);

        // 如果该玩家在被释放前已经在肚内死亡，则不尝试复活/改变其游戏模式或传送，
        // 以免后续释放（例如鹈鹕死亡）将其复活。仅清理跟踪状态并触发语音释放。
        if (stashedDead.contains(targetId)) {
            stashedDead.remove(targetId);
            target.setInvisible(false);
            target.connection.send(new ClientboundSetCameraPacket(target));
            NoellesrolesVoiceChatPlugin.onPelicanRelease(targetId);
            return;
        }

        target.setGameMode(restoreMode == GameType.SPECTATOR ? GameType.ADVENTURE : restoreMode);
        target.setInvisible(false);
        target.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot());
        target.connection.send(new ClientboundSetCameraPacket(target));

        // 如果被释放的是疫使，重新检查疫使时刻的触发条件
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(target.level());
        if (gameWorld != null && gameWorld.isRole(target, ModRoles.INFECTED)) {
            InfectedWinChecker.onInfectedReleasedFromPelican((ServerLevel) target.level());
        }

        NoellesrolesVoiceChatPlugin.onPelicanRelease(targetId);
    }

    private static void releasePlayerFromTick(ServerPlayer target) {
        // Deprecated: tick now calls releasePlayer directly. Keep method as shim to
        // preserve compatibility but delegate to releasePlayer.
        releasePlayer(target);
    }

    public static void releaseAllForPelican(UUID pelicanId, MinecraftServer server) {
        Deque<UUID> belly = stashedByPelican.get(pelicanId);
        if (belly == null || belly.isEmpty())
            return;
        for (UUID targetId : new ArrayList<>(belly)) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetId);
            if (target != null) {
                releasePlayer(target);
            } else {
                pelicanByStashed.remove(targetId);
                belly.remove(targetId);
            }
        }
        if (belly.isEmpty())
            stashedByPelican.remove(pelicanId);

        // 同步清理鹈鹕组件中的肚内玩家列表，防止再次按技能键时重复显示"吐出玩家"
        ServerPlayer pelican = server.getPlayerList().getPlayer(pelicanId);
        if (pelican != null) {
            PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(pelican);
            comp.bellyPlayerIds.clear();
            comp.bellyNames.clear();
            comp.sync();
        }

        // 如果被释放的玩家中有疫使，重新检查疫使时刻触发条件
        ServerLevel overworld = server.overworld();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(overworld);
        if (gameWorld != null) {
            InfectedWinChecker.onInfectedReleasedFromPelican(overworld);
        }
    }

    public static void releaseAllInWorld(ServerLevel world) {
        for (UUID pelicanId : List.copyOf(stashedByPelican.keySet())) {
            releaseAllForPelican(pelicanId, world.getServer());
        }
    }

    public static boolean isStashed(Player player) {
        return player != null && pelicanByStashed.containsKey(player.getUUID());
    }

    public static boolean isStashed(UUID playerId) {
        return playerId != null && pelicanByStashed.containsKey(playerId);
    }

    public static UUID getPelicanForStashed(UUID playerId) {
        return pelicanByStashed.get(playerId);
    }

    public static Set<UUID> getBellyReceivers(UUID senderId) {
        if (senderId == null)
            return Set.of();
        UUID pelicanId = pelicanByStashed.get(senderId);
        if (pelicanId != null) {
            Set<UUID> receivers = ConcurrentHashMap.newKeySet();
            receivers.add(pelicanId);
            Deque<UUID> belly = stashedByPelican.get(pelicanId);
            if (belly != null)
                receivers.addAll(belly);
            receivers.remove(senderId);
            return receivers;
        }
        Deque<UUID> belly = stashedByPelican.get(senderId);
        if (belly == null || belly.isEmpty())
            return Set.of();
        return Set.copyOf(belly);
    }

    public static boolean shouldCancelVoice(UUID senderId, UUID receiverId) {
        if (senderId == null || receiverId == null || senderId.equals(receiverId))
            return false;
        UUID senderPelican = pelicanByStashed.get(senderId);
        if (senderPelican != null) {
            return !receiverId.equals(senderPelican) && !senderPelican.equals(pelicanByStashed.get(receiverId));
        }
        UUID receiverPelican = pelicanByStashed.get(receiverId);
        if (receiverPelican != null) {
            return !senderId.equals(receiverPelican);
        }
        return false;
    }

    public static void clearAll() {
        pelicanByStashed.clear();
        stashedByPelican.clear();
        stashedPreviousGameMode.clear();
    }

    /**
     * 处理被吞噬玩家死亡 - 清理鹈鹕数据并恢复玩家状态，使其正常进入死亡
     */
    public static void onStashedPlayerDeath(ServerPlayer target) {
        UUID targetId = target.getUUID();
        UUID pelicanId = pelicanByStashed.remove(targetId);
        if (pelicanId != null) {
            Deque<UUID> belly = stashedByPelican.get(pelicanId);
            if (belly != null) {
                belly.remove(targetId);
                if (belly.isEmpty())
                    stashedByPelican.remove(pelicanId);
            }
        }
        stashedPreviousGameMode.remove(targetId);

        // 标记该玩家在肚内已死亡，后续任何释放流程都不应尝试复活或改变其死亡状态
        stashedDead.add(targetId);

        // 恢复聊天并清除 DeathPenalty/起搏器
        DeathPenaltyComponent.KEY.get(target).init();
        DefibrillatorComponent.KEY.get(target).init();

        // 清除被吞噬时添加的禁用技能、禁用聊天栏和禁用使用物品效果
        target.removeEffect(ModEffects.SKILL_BANED);
        target.removeEffect(ModEffects.CHAT_BAN);
        target.removeEffect(ModEffects.USED_BANED);

        // 不要在死亡处理期间强制改变玩家为冒险模式，允许死亡流程将玩家置为旁观者
        target.setInvisible(false);
        target.connection.send(new ClientboundSetCameraPacket(target));

        NoellesrolesVoiceChatPlugin.onPelicanRelease(targetId);
    }

    public static void onPelicanDeath(ServerPlayer pelican) {
        UUID pelicanId = pelican.getUUID();
        Deque<UUID> belly = stashedByPelican.get(pelicanId);
        if (belly == null || belly.isEmpty())
            return;
        for (UUID targetId : new ArrayList<>(belly)) {
            ServerPlayer target = pelican.getServer().getPlayerList().getPlayer(targetId);
            // 无论玩家是否在线，都必须从追踪映射中移除，防止离线玩家重连后被意外复活
            pelicanByStashed.remove(targetId);
            stashedPreviousGameMode.remove(targetId);
            if (target != null) {

                // 恢复聊天并清除 DeathPenalty/起搏器
                DeathPenaltyComponent.KEY.get(target).init();
                DefibrillatorComponent.KEY.get(target).init();
                // 清除被吞噬时添加的禁用技能、禁用聊天栏和禁用使用物品效果
                target.removeEffect(ModEffects.SKILL_BANED);
                target.removeEffect(ModEffects.CHAT_BAN);
                target.removeEffect(ModEffects.USED_BANED);
            // 如果该玩家在肚内已死亡，跳过复活/改模式/传送。
            if (stashedDead.contains(targetId)) {
                stashedDead.remove(targetId);
                target.setInvisible(false);
                target.connection.send(new ClientboundSetCameraPacket(target));
                NoellesrolesVoiceChatPlugin.onPelicanRelease(targetId);
                target.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.spat_out_dead"),
                    true);
            } else {
                target.setGameMode(GameType.ADVENTURE);
                target.setInvisible(false);
                target.teleportTo(pelican.serverLevel(), pelican.getX(), pelican.getY(), pelican.getZ(),
                    pelican.getYRot(), pelican.getXRot());
                target.connection.send(new ClientboundSetCameraPacket(target));
                NoellesrolesVoiceChatPlugin.onPelicanRelease(targetId);
                target.displayClientMessage(
                    Component.translatable("message.noellesroles.pelican.spat_out_dead"),
                    true);
            }
            }
            belly.remove(targetId);
        }
        stashedByPelican.remove(pelicanId);

        // 如果被释放的玩家中有疫使，重新检查疫使时刻触发条件
        ServerLevel overworld = pelican.serverLevel();
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(overworld);
        if (gameWorld != null) {
            InfectedWinChecker.onInfectedReleasedFromPelican(overworld);
        }
    }

    public static void onLastStand(ServerLevel world) {
        for (UUID pelicanId : List.copyOf(stashedByPelican.keySet())) {
            releaseAllForPelican(pelicanId, world.getServer());
        }
    }
}
