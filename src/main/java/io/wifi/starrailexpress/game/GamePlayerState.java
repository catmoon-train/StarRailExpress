/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import io.wifi.starrailexpress.cca.ParticipationComponent;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;
import io.wifi.starrailexpress.content.command.StaminaCommand;
import io.wifi.starrailexpress.event.GameUtilityEvents;
import io.wifi.starrailexpress.network.original.AnnounceEndingPayload;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.events.ResetPlayerEvent;
import org.agmas.noellesroles.api.time.TimeRewindPlayback;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.events.NRDeathEvents;
import org.agmas.noellesroles.init.events.NRGameStateEvents;
import org.agmas.noellesroles.packet.RefreshDimensionsS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.wifi.starrailexpress.game.GameUtils.*;

/** 玩家重置、能力基线、参与状态和特殊存活判定。 */
final class GamePlayerState {
    private static final float SPEED_EPSILON = 1.0E-4F;

    private GamePlayerState() {
    }

    static void resetPlayerAbilities(ServerPlayer player) {
        var abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        abilities.instabuild = false;
        abilities.invulnerable = false;
        abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
        abilities.setWalkingSpeed(DEFAULT_WALKING_SPEED);
        player.onUpdateAbilities();
    }

    static void normalizeSpectatorFlightAbilities(ServerPlayer player) {
        var abilities = player.getAbilities();
        if (abilities.mayfly
                && Math.abs(abilities.getFlyingSpeed() - DEFAULT_FLYING_SPEED) <= SPEED_EPSILON
                && Math.abs(abilities.getWalkingSpeed() - DEFAULT_WALKING_SPEED) <= SPEED_EPSILON) return;
        // 刻意不动 flying，旁观者飞行开关由客户端接管，避免刚死就下坠。
        abilities.mayfly = true;
        abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
        abilities.setWalkingSpeed(DEFAULT_WALKING_SPEED);
        player.onUpdateAbilities();
    }

    static void releaseRoleFlight(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative()) {
            normalizeSpectatorFlightAbilities(player);
            return;
        }
        var abilities = player.getAbilities();
        abilities.mayfly = false;
        abilities.flying = false;
        abilities.setFlyingSpeed(DEFAULT_FLYING_SPEED);
        abilities.setWalkingSpeed(DEFAULT_WALKING_SPEED);
        player.fallDistance = 0;
        player.onUpdateAbilities();
    }

    static void resetPlayer(ServerPlayer player) {
        SREItemUtils.clearItem(player, item -> true, -1);
        resetPlayerAbilities(player);
        player.fallDistance = 0;
        InfectedPlayerComponent.KEY.get(player).clear();
        SRERoleDataPlayerComponent.KEY.get(player).clear();
        SREPlayerMoodComponent.KEY.get(player).clear();
        SREPlayerShopComponent.KEY.get(player).clear();
        io.wifi.starrailexpress.cca.DynamicShopComponent.KEY.get(player).clear();
        SREPlayerPoisonComponent.KEY.get(player).clear();
        SREPlayerPsychoComponent.KEY.get(player).clear();
        SREPlayerNoteComponent.KEY.get(player).clear();
        SREArmorPlayerComponent.KEY.get(player).clear();
        io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player).clear();
        ResetPlayerEvent.EVENT.invoker().resetPlayer(player);
        HeliumBuzzPlayerComponent.KEY.get(player).clear();
        if (!TrainVoicePlugin.isVoiceChatMissing()) TrainVoicePlugin.resetPlayer(player.getUUID());
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        if (player.isSleeping()) player.stopSleeping();
        player.removeVehicle();
        ExtraSlotComponent.KEY.get(player).clear();
        player.setInvulnerable(false);
        player.setNoGravity(false);
        // -1 代表职业最大体力。
        StaminaCommand.setStamina(player, -1);
        player.setLastHurtByMob(null);
        player.setLastHurtByPlayer(null);
        player.refreshDimensions();
        GameUtilityEvents.AFTER_RESET.invoker().afterReset(player);
    }

    static void resetPlayerAfterGame(ServerPlayer player) {
        resetPlayer(player);
        // 局末直接清修饰符绕过 ModifierRemoved，补包使客户端重算伪装眼高。
        ServerPlayNetworking.send(player, new RefreshDimensionsS2CPacket());
        ServerPlayNetworking.send(player, new AnnounceEndingPayload());
        player.removeVehicle();
        BlockPos worldSpawn = player.serverLevel().getSharedSpawnPos();
        float worldAngle = player.serverLevel().getSharedSpawnAngle();
        var spawn = new AreasWorldComponent.PosWithOrientation(
                new Vec3(worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ()), worldAngle, 0);
        player.setCamera(player);
        player.teleportTo(player.getServer().overworld(), spawn.pos.x, spawn.pos.y, spawn.pos.z,
                player.getYRot(), player.getXRot());
    }

    static boolean differentTeam(SRERole role1, SRERole role2) {
        if (role1 == null || role2 == null) return false;
        if (role1.isVigilanteTeam() && role2.isVigilanteTeam()) return false;
        if (role1.isCanUseKiller() && role2.isCanUseKiller()) return false;
        if (role1.isNeutralForKiller() && role2.isCanUseKiller()) return false;
        if (role1.isCanUseKiller() && role2.isNeutralForKiller()) return false;
        if (role1.isNeutralForKiller() && role2.isNeutralForKiller()) return false;
        if (role1.isInnocent() && role2.isInnocent()) return false;
        return true;
    }

    static boolean differentTeam(Player player1, Player player2) {
        if (player1 == null || player2 == null) return false;
        return differentTeam(SREGameWorldComponent.getInstance(player1).getAllegianceRole(player1),
                SREGameWorldComponent.getInstance(player2).getAllegianceRole(player2));
    }

    static boolean isPlayerEliminatedIgnoreShitSplit(Player player) {
        return player == null || !player.isAlive() || player.isCreative() || player.isSpectator();
    }

    static boolean isPlayerEliminated(Player player) {
        if (isPlayerReallyAliveOrDead(player) == SPAliveResult.ALIVE) return false;
        return player == null || !player.isAlive() || player.isCreative() || player.isSpectator();
    }

    static boolean isPlayerSpectator(Player player) {
        if (player == null || isPlayerReallyAliveOrDead(player) == SPAliveResult.ALIVE) return false;
        return player.isSpectator();
    }

    static boolean isPlayerAliveAndSurvivalIgnoreShitSplit(Player player) {
        var override = GameUtilityEvents.ALIVE_OVERRIDE.invoker().query(player);
        if (!override.isPass()) return override.isTrue();
        return player != null && !player.isSpectator() && !player.isCreative();
    }

    static boolean isPlayerAliveAndSurvival(Player player, WorldModifierComponent modifiers) {
        if (player == null) return false;
        if (isPlayerReallyAliveOrDead(player, modifiers) == SPAliveResult.ALIVE) return true;
        return isPlayerAliveAndSurvivalIgnoreShitSplit(player);
    }

    static boolean isPlayerAliveAndSurvival(Player player) {
        if (player == null) return false;
        return isPlayerAliveAndSurvival(player, WorldModifierComponent.KEY.get(player.level()));
    }

    static boolean isPlayerCreative(Player player) {
        return player != null && player.isCreative();
    }

    static boolean isPlayerSpectatingOrCreative(Player player) {
        if (player == null || isPlayerReallyAliveOrDead(player) == SPAliveResult.ALIVE) return false;
        return isPlayerSpectatingOrCreativeIgnoreShitSplit(player);
    }

    static SPAliveResult isPlayerReallyAliveOrDead(Player player) {
        if (player == null) return SPAliveResult.DEAD;
        return isPlayerReallyAliveOrDead(player, WorldModifierComponent.KEY.get(player.level()));
    }

    static SPAliveResult isPlayerReallyAliveOrDead(Player player, WorldModifierComponent modifiers) {
        if (player == null) return SPAliveResult.DEAD;
        if (player.hasEffect(ModEffects.TIME_REWIND_MARK) || TimeRewindPlayback.isActive(player.getUUID())) {
            return SPAliveResult.ALIVE;
        }
        if (modifiers.isModifier(player, SEModifiers.SPLIT_PERSONALITY)) {
            if (player.isSpectator()) {
                return SplitPersonalityComponent.KEY.get(player).isDeath() ? SPAliveResult.DEAD : SPAliveResult.ALIVE;
            } else if (player.isCreative()) {
                return SPAliveResult.DEAD;
            } else {
                return SPAliveResult.ALIVE;
            }
        }
        return SPAliveResult.NOT;
    }

    static boolean isPlayerSpectatingOrCreativeIgnoreShitSplit(Player player) {
        return player != null && (player.isSpectator() || player.isCreative());
    }

    static int getReadyPlayerCount(Level world) {
        List<? extends Player> players = world.players();
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(world);
        ParticipationComponent participation = ParticipationComponent.KEY.get(world);
        Set<UUID> ready = new HashSet<>(participation.getSettlementViewers());
        players.stream().filter(participation::isParticipating)
                .filter(p -> areas.getReadyArea().contains(p.position())).forEach(p -> ready.add(p.getUUID()));
        ready.removeIf(id -> !participation.isParticipating(id));
        return ready.size();
    }

    static int getParticipatingPlayerCount(Level world) {
        return ParticipationComponent.KEY.get(world).getParticipatingOnlineCount();
    }

    static int getOptedOutPlayerCount(Level world) {
        return ParticipationComponent.KEY.get(world).getOptedOutOnlineCount();
    }

    static long getAlivePlayerCount(Level level) {
        return level.players().stream().filter(GamePlayerState::isPlayerAliveAndSurvivalIgnoreShitSplit).count();
    }

    static void reJudgeSpectatorsPenalty(Level world) {
        if (world.isClientSide) return;
        NRDeathEvents.reJudgeSpectatorsPenalty(world);
    }

    static void pendingReJudgeSpectatorsPenalty() {
        NRGameStateEvents.pendingRejudgingSpectatorDeathPeanlty.set(true);
    }

    static boolean shouldGiveSpectatorsDeathPenalty(Player player) {
        var game = SREGameWorldComponent.getInstance(player);
        return game.isRole(player, ModRoles.CONSPIRATOR) || game.isRole(player, ModRoles.ANATMAN)
                || game.isRole(player, ModRoles.ASATYA);
    }

    static String spectatorsDeathPenaltyMessageKey(Player penalized) {
        if (penalized == null || penalized.level() == null) return "message.noellesroles.penalty.limit.god_job_couple";
        var game = SREGameWorldComponent.getInstance(penalized);
        for (Player player : penalized.level().players()) {
            if (!isPlayerAliveAndSurvival(player)) continue;
            if (game.isRole(player, ModRoles.ANATMAN) || game.isRole(player, ModRoles.ASATYA)) {
                return "message.noellesroles.penalty.limit.anatman_asatya";
            }
        }
        return "message.noellesroles.penalty.limit.god_job_couple";
    }

    static void refreshPlayerDimension(ServerPlayer player) {
        player.refreshDimensions();
        ServerPlayNetworking.send(player, new RefreshDimensionsS2CPacket());
    }
}
