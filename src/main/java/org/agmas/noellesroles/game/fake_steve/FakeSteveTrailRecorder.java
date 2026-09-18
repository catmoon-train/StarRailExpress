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

package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记录玩家「生前」的移动轨迹，供伪人替换后当巡逻路线使用。
 *
 * <p>
 * 伪人原本完全靠 A* 找路，一旦搜不到路（或人群侧移让移动包被租约拒绝），身体就会卡在原地只转
 * 脑袋。玩家自己走出去的路线天然可行走、也不需要在运行时重新搜索，所以这里把路线存成路点，
 * 替换时交给 {@link FakeSteveAi#driveTrail} 直接沿点走。
 *
 * <p>
 * 只在玩家「踩到地面或浮在水里」时采样，并且每个路点都要通过
 * {@link FakeSteveNavigator#safeStand} 校验，所以路线里不会出现跑跳的半空坐标或站不住的格子。
 *
 * <p>
 * 性能取舍（每 tick 成本接近 0）：
 * <ul>
 * <li>只在每 {@link #SAMPLE_INTERVAL_TICKS} tick（默认 0.5 秒）采样一次；</li>
 * <li>相对上一个采样点位移不足 {@link #MIN_STEP_DISTANCE} 格直接返回，站着不动不产生任何数据；</li>
 * <li>每个玩家固定最多 {@link #MAX_POINTS} 个路点（超出丢弃最旧点），单人内存约数 KB；</li>
 * <li>不上传客户端、不写 NBT，对局结束或玩家断线即清理，不跨局残留。</li>
 * </ul>
 */
public final class FakeSteveTrailRecorder {

    /**
     * 采样间隔（tick）。0.2 秒一次，配合 {@link #MIN_STEP_DISTANCE} 能做到「走一格记一格」，
     * 拐角才不会被拉成斜线；成本只是每 4 tick 给每个玩家做几次方块判定。
     */
    public static final int SAMPLE_INTERVAL_TICKS = 4;

    /** 相邻采样点的最小间距（格）。不足一格视为原地微动，不记录。 */
    public static final double MIN_STEP_DISTANCE = 1.0D;

    /** 单个玩家保留的最大路点数（超出丢弃最旧点，保留最近的一段路线）。 */
    public static final int MAX_POINTS = 384;

    /**
     * 上一个采样点刚记下不久、这次却跳了这么远，就判定为传送/换场景（正常跑动零点几秒走不了这么远），
     * 旧路线作废重新记录。若只是因为采样被跳过而拉开的距离，不当作传送。
     */
    private static final double TELEPORT_JUMP_DISTANCE = 8.0D;

    /** 跑跳/腾空时向下投影找可站地面的最大距离（格）。 */
    private static final int MAX_GROUND_DROP = 3;

    /** 路点少于该数量就不值得当巡逻路线用。 */
    public static final int MIN_USEFUL_POINTS = 8;

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    private FakeSteveTrailRecorder() {
    }

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(FakeSteveTrailRecorder::tickLevel);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> forget(handler.player.getUUID())));
    }

    private static void tickLevel(ServerLevel level) {
        // 非对局时间（大厅、结算后）不记录，避免把待机走动混进巡逻路线。
        if (!SREGameWorldComponent.KEY.get(level).isRunning()) {
            return;
        }
        if (level.getGameTime() % SAMPLE_INTERVAL_TICKS != 0L) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            // 已被替换的身体由 AI 驱动，它的移动不算「生前」轨迹。
            if (FakeSteveDirector.isReplaced(player)) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            record(player, level.getGameTime());
        }
    }

    /**
     * 把玩家的实时位置投影成「双脚真正能站的那一格」，也就是路线上真实的一个点。
     *
     * <p>站在地面上时就是脚下那一格；跑跳/腾空时向下找最近的可站格，
     * 这样路线仍然是玩家真正走过的那条地面路线，不会因为采样正好落在半空而把拐角拉成斜线。
     *
     * @return 可站的路点；悬空且脚下 3 格内都站不住时返回 null（该帧不记录）
     */
    private static BlockPos routePoint(ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        ServerLevel level = player.serverLevel();
        for (int drop = 0; drop <= MAX_GROUND_DROP; drop++) {
            BlockPos candidate = feet.below(drop);
            if (FakeSteveNavigator.standable(level, candidate)) {
                return candidate;
            }
        }
        // 深水里没有支撑方块：保留原位置（伪人会游过去）。
        return player.isInWater() ? feet : null;
    }

    private static void record(ServerPlayer player, long now) {
        BlockPos pos = routePoint(player);
        if (pos == null) {
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(player.getUUID(), id -> new Trail());
        BlockPos previous = null;
        if (!trail.isEmpty()) {
            previous = trail.last();
            if (previous.equals(pos)) {
                return;
            }
            double distanceSqr = previous.distSqr(pos);
            if (distanceSqr < MIN_STEP_DISTANCE * MIN_STEP_DISTANCE) {
                return;
            }
            if (now - trail.lastTick() <= SAMPLE_INTERVAL_TICKS * 2L
                    && distanceSqr > TELEPORT_JUMP_DISTANCE * TELEPORT_JUMP_DISTANCE) {
                trail.clear();
                previous = null;
            }
        }
        if (previous != null) {
            addCornerPoint(player.serverLevel(), trail, previous, pos, now);
        }
        trail.add(pos, now);
    }

    /**
     * 斜着走时补一个直角拐点。
     *
     * <p>
     * 玩家拐弯的瞬间，两次采样可能正好落在斜向相邻的两格上，而这两格的直角一侧很可能是墙角。
     * 只记斜线的话，伪人沿这条线走就会「切角」撞在墙上，然后要等满 3 秒的撞墙判定才会重新选点。
     * 补一个站在玩家真实走位那一侧的直角点，路线就贴着走廊了。
     *
     * <p>
     * 只在斜向采样时触发（正常直走完全不加成本），并且只做 1~2 次
     * {@link FakeSteveNavigator#standable} 判定；两个直角点都站不住时保持原样。
     */
    private static void addCornerPoint(ServerLevel level, Trail trail,
            BlockPos previous, BlockPos pos, long now) {
        int dx = pos.getX() - previous.getX();
        int dz = pos.getZ() - previous.getZ();
        if (dx == 0 || dz == 0 || pos.getY() != previous.getY()) {
            return;
        }
        BlockPos corner = previous.offset(dx, 0, 0);
        if (!FakeSteveNavigator.standable(level, corner)) {
            corner = previous.offset(0, 0, dz);
        }
        if (!corner.equals(previous) && !corner.equals(pos)
                && FakeSteveNavigator.standable(level, corner)) {
            trail.add(corner, now);
        }
    }

    /**
     * 把该玩家记录到的路线交给伪人 AI，并以「离身体最近的采样点」作为起点。
     *
     * @return 是否成功装载路线（路点不足时为 false，AI 会退回原来的 A* 徘徊逻辑）
     */
    static boolean capture(ServerPlayer player, FakeSteveAgentState state) {
        Trail trail = TRAILS.get(player.getUUID());
        if (trail == null || trail.size() < MIN_USEFUL_POINTS) {
            return false;
        }
        List<BlockPos> points = new ArrayList<>(trail.size());
        BlockPos previous = null;
        for (BlockPos pos : trail.points()) {
            // 去掉相邻重复点，让路点更接近「拐点」。
            if (previous != null && previous.equals(pos)) {
                continue;
            }
            points.add(pos);
            previous = pos;
        }
        if (points.size() < MIN_USEFUL_POINTS) {
            return false;
        }
        state.trail.clear();
        state.trail.addAll(points);
        state.trailIndex = nearestIndex(points, player.blockPosition());
        state.trailDirection = 1;
        state.trailWaypointTick = 0L;
        state.trailLoops = 0;
        // 记下这条路线属于哪个维度：卡死保底传送前要确认坐标还对得上。
        state.trailDimension = player.level().dimension();
        state.trailEnabled = true;
        return true;
    }

    /** 路线中离 {@code origin} 最近的路点下标。 */
    static int nearestIndex(List<BlockPos> points, BlockPos origin) {
        int best = 0;
        double bestSqr = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            double distanceSqr = points.get(i).distSqr(origin);
            if (distanceSqr < bestSqr) {
                bestSqr = distanceSqr;
                best = i;
            }
        }
        return best;
    }

    static void forget(UUID playerId) {
        TRAILS.remove(playerId);
    }

    /** 对局结束清理，避免路线跨局残留。 */
    static void clearAll() {
        TRAILS.clear();
    }

    /** 定长环形缓冲：超出 {@link #MAX_POINTS} 丢弃最旧路点。 */
    private static final class Trail {
        private final ArrayList<BlockPos> points = new ArrayList<>(MAX_POINTS);
        private long lastTick;

        private boolean isEmpty() {
            return points.isEmpty();
        }

        private int size() {
            return points.size();
        }

        private BlockPos last() {
            return points.get(points.size() - 1);
        }

        private long lastTick() {
            return lastTick;
        }

        private List<BlockPos> points() {
            return points;
        }

        private void add(BlockPos pos, long tick) {
            if (points.size() >= MAX_POINTS) {
                points.remove(0);
            }
            points.add(pos.immutable());
            lastTick = tick;
        }

        private void clear() {
            points.clear();
            lastTick = 0L;
        }
    }
}
