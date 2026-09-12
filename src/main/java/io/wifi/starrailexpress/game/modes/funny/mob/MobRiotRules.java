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

package io.wifi.starrailexpress.game.modes.funny.mob;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.event.AllowPlayerWin;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnMeetingStart;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MobRiotRules {
    public static final String MODE_PATH = "mob_riot";
    public static final double ROOM_RADIUS = 8.0;

    private static final Map<String, Boolean> MAP_ELIGIBILITY_CACHE = new ConcurrentHashMap<>();

    private MobRiotRules() {
    }

    public static void registerEvents() {
        OnGameTrueStarted.EVENT.register(serverLevel -> {
            if (SREGameWorldComponent.KEY.get(serverLevel).getGameMode() instanceof SREMobRiotGameMode mob) {
                mob.onTrueStarted(serverLevel);
            }
        });
        AllowPlayerWin.EVENT.register((world, player, playerRole, winStatus, roundEnd, gameComponent) -> {
            if (!(gameComponent.getGameMode() instanceof SREMobRiotGameMode mob)) {
                return TrueFalseResult.PASS;
            }
            if (mob.hasEscaped(player.getUUID())) {
                return TrueFalseResult.TRUE;
            }
            if (mob.isEscapeUnlocked()
                    && (winStatus == WinStatus.PASSENGERS || winStatus == WinStatus.TIME)
                    && playerRole != null
                    && playerRole.isNeutrals()
                    && !playerRole.winWithInnocent()) {
                return TrueFalseResult.FALSE;
            }
            return TrueFalseResult.PASS;
        });
        OnMeetingStart.ALLOW_MEETING.register((serverLevel, reporter, victim, emergency) -> {
            if (!isNight(serverLevel)) {
                return TrueFalseResult.PASS;
            }
            if (reporter != null) {
                reporter.displayClientMessage(
                        Component.translatable("message.sre.mob_riot.night_no_vote")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return TrueFalseResult.FALSE;
        });
    }

    public static boolean isActive(Level level) {
        return level != null
                && SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMobRiotGameMode;
    }

    public static boolean isNight(Level level) {
        return SREGameWorldComponent.KEY.get(level).getGameMode() instanceof SREMobRiotGameMode mob
                && mob.isNight();
    }

    public static boolean isKillerCamp(SRERole role) {
        return role != null && role.canUseKiller() && !role.isInnocent();
    }

    public static boolean isKillerOrKillerNeutral(SRERole role) {
        return role != null && !role.isInnocent() && (role.canUseKiller() || role.isNeutralForKiller());
    }

    public static boolean canSleepEscape(SRERole role) {
        return role != null && (role.isInnocent() || role.isNeutrals());
    }

    public static int tokenGoal(int startingPlayers) {
        int perPlayer = Math.max(1, SREConfig.instance().mobRiotTokenPerPlayer);
        return Math.max(1, startingPlayers) * perPlayer;
    }

    public static int sumTokens(MinecraftServer server) {
        int sum = 0;
        if (server == null) {
            return 0;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SREPlayerMinigameTaskComponent component = SREPlayerMinigameTaskComponent.KEY.get(player);
            if (component != null) {
                sum += component.getTokens();
            }
        }
        return sum;
    }

    public static boolean hasLivingUnescapedInnocent(ServerLevel level, SREMobRiotGameMode mode,
            SREGameWorldComponent gameComponent) {
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerEliminated(player) || mode.hasEscaped(player.getUUID())) {
                continue;
            }
            if (gameComponent.canIncreaseSurvivingInnocents(player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInOwnRoom(ServerPlayer player) {
        Integer room = GameUtils.roomToPlayer.get(player.getUUID());
        if (room == null) {
            return player.isSleeping();
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(player.level());
        Vec3 spawn = GameUtils.getSpawnPos(areas, room);
        if (spawn == null) {
            return player.isSleeping();
        }
        return player.position().distanceToSqr(spawn) <= ROOM_RADIUS * ROOM_RADIUS;
    }

    public static boolean isCurrentMapEligible(AreasWorldComponent areas) {
        return areas != null
                && areas.areasSettings != null
                && areas.areasSettings.meetingEnabled
                && areas.areasSettings.minigameQuestEnabled;
    }

    public static boolean isVoteMapEligible(MinecraftServer server, String mapId) {
        if (mapId == null || mapId.isBlank()) {
            return false;
        }
        return MAP_ELIGIBILITY_CACHE.computeIfAbsent(mapId, id -> readMapFlags(server, id));
    }

    public static void clearMapEligibilityCache() {
        MAP_ELIGIBILITY_CACHE.clear();
    }

    private static boolean readMapFlags(MinecraftServer server, String mapId) {
        if (server == null) {
            return false;
        }
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("train_maps").toAbsolutePath().normalize();
        Path path = dir.resolve(mapId + ".json").normalize();
        if (!path.startsWith(dir) || !Files.isRegularFile(path)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("settings") || !root.get("settings").isJsonObject()) {
                return false;
            }
            JsonObject settings = root.getAsJsonObject("settings");
            return bool(settings, "meetingEnabled") && bool(settings, "minigameQuestEnabled");
        } catch (Exception e) {
            SRE.LOGGER.warn("Failed to read mob riot map flags for {}", mapId, e);
            return false;
        }
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsBoolean();
    }
}
