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

package net.exmo.sre.planecrash;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.network.PacketTracker;
import io.wifi.starrailexpress.util.Scheduler;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 飞机坠落地图事件：开局刷新飞机，并把开场镜头切到飞机；对局中每隔 60–90 秒震颤一次。
 */
public final class PlaneCrashManager {
    public static final int INTRO_DURATION_TICKS = 120;
    public static final int TREMOR_DURATION_TICKS = 36;
    private static final int TREMOR_MIN_TICKS = 20 * 60;
    private static final int TREMOR_EXTRA_TICKS = 20 * 30;

    private static final Map<ServerLevel, Runtime> RUNTIMES = new WeakHashMap<>();

    private PlaneCrashManager() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world instanceof ServerLevel serverLevel) {
                tick(serverLevel);
            }
        });
        OnGameEnd.EVENT.register((serverLevel, game) -> clear(serverLevel));
    }

    public static boolean isEnabled(ServerLevel level) {
        AreasSettings settings = AreasWorldComponent.KEY.get(level).areasSettings;
        return settings != null && settings.planeCrashEventEnabled;
    }

    /** 对局开始：初始化震颤计时。飞机本身等到开场镜头时再刷，避免轮选把飞机飞完。 */
    public static void onGameStarted(ServerLevel level) {
        if (!isEnabled(level)) {
            RUNTIMES.remove(level);
            return;
        }
        Runtime runtime = new Runtime();
        runtime.tiltYaw = AreasWorldComponent.KEY.get(level).areasSettings.planeCrashTiltYaw;
        runtime.nextTremorTick = nextInterval(level);
        RUNTIMES.put(level, runtime);
    }

    /**
     * 刷出飞机并向参与者发送聚焦飞机的开场镜头。
     *
     * @return 已接管开场镜头时返回 true，调用方不应再发默认 intro
     */
    public static boolean startIntro(ServerLevel level) {
        if (!isEnabled(level)) {
            return false;
        }
        Runtime runtime = RUNTIMES.computeIfAbsent(level, ignored -> {
            Runtime created = new Runtime();
            created.tiltYaw = AreasWorldComponent.KEY.get(level).areasSettings.planeCrashTiltYaw;
            created.nextTremorTick = nextInterval(level);
            return created;
        });
        CrashPlaneEntity plane = spawnPlane(level, runtime);
        if (plane == null) {
            return false;
        }
        runtime.planeId = plane.getId();
        Scheduler.schedule(() -> sendIntro(level, plane), 4);
        return true;
    }

    static void onPlaneCrashed(ServerLevel level, float tiltYaw) {
        Runtime runtime = RUNTIMES.get(level);
        if (runtime != null) {
            runtime.planeId = -1;
            runtime.nextTremorTick = GameUtils.getTicksFromGameStart(level) + nextInterval(level);
        }
        triggerTremor(level, tiltYaw);
    }

    public static void clear(ServerLevel level) {
        RUNTIMES.remove(level);
        for (var entity : level.getAllEntities()) {
            if (entity instanceof CrashPlaneEntity plane) {
                plane.discard();
            }
        }
    }

    private static void tick(ServerLevel level) {
        if (!SREGameWorldComponent.KEY.get(level).isRunning() || !isEnabled(level)) {
            return;
        }
        Runtime runtime = RUNTIMES.get(level);
        if (runtime == null) {
            return;
        }
        long now = GameUtils.getTicksFromGameStart(level);
        if (now >= runtime.nextTremorTick) {
            triggerTremor(level, runtime.tiltYaw);
            runtime.nextTremorTick = now + nextInterval(level);
        }
    }

    private static void triggerTremor(ServerLevel level, float tiltYaw) {
        double rad = Math.toRadians(tiltYaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        PlaneCrashTremorPayload payload = new PlaneCrashTremorPayload(tiltYaw, TREMOR_DURATION_TICKS);
        for (ServerPlayer player : level.players()) {
            PacketTracker.sendToClient(player, payload);
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            player.setDeltaMovement(dx * 0.95D, 0.22D, dz * 0.95D);
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
    }

    private static CrashPlaneEntity spawnPlane(ServerLevel level, Runtime runtime) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AreasSettings settings = areas.areasSettings;
        AABB playArea = areas.getPlayArea();
        Vec3 center = playArea.getCenter();
        if (playArea.getXsize() < 2.0D && playArea.getZsize() < 2.0D) {
            center = new Vec3(level.getSharedSpawnPos().getX() + 0.5D,
                    level.getSharedSpawnPos().getY() + 8.0D,
                    level.getSharedSpawnPos().getZ() + 0.5D);
        }
        float tiltYaw = settings.planeCrashTiltYaw;
        runtime.tiltYaw = tiltYaw;
        double distance = settings.planeCrashSpawnDistance;
        if (distance <= 0.0D) {
            distance = Math.max(96.0D, Math.hypot(playArea.getXsize(), playArea.getZsize()) * 0.55D + 72.0D);
        }
        double height = settings.planeCrashSpawnHeight > 0.0F ? settings.planeCrashSpawnHeight : 36.0D;
        double rad = Math.toRadians(tiltYaw);
        double fx = -Math.sin(rad);
        double fz = Math.cos(rad);
        double spawnY = playArea.maxY + height;
        Vec3 spawn = new Vec3(center.x - fx * distance, spawnY, center.z - fz * distance);
        Vec3 target = new Vec3(center.x, Math.max(playArea.minY + 8.0D, center.y + 6.0D), center.z);
        double speed = spawn.distanceTo(target) / CrashPlaneEntity.DEFAULT_FLIGHT_TICKS;

        CrashPlaneEntity plane = TMMEntities.CRASH_PLANE.create(level);
        if (plane == null) {
            return null;
        }
        plane.setupFlight(spawn, target, tiltYaw, speed);
        level.addFreshEntity(plane);
        return plane;
    }

    private static void sendIntro(ServerLevel level, CrashPlaneEntity plane) {
        if (!plane.isAlive()) {
            return;
        }
        PlaneCrashIntroPayload payload = new PlaneCrashIntroPayload(plane.getId(), INTRO_DURATION_TICKS);
        for (ServerPlayer player : level.players()) {
            if (player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                PacketTracker.sendToClient(player, payload);
            }
        }
    }

    private static int nextInterval(ServerLevel level) {
        return TREMOR_MIN_TICKS + level.random.nextInt(TREMOR_EXTRA_TICKS + 1);
    }

    private static final class Runtime {
        long nextTremorTick;
        float tiltYaw;
        int planeId = -1;
    }
}
