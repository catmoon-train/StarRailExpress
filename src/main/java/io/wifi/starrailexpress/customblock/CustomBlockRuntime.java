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

package io.wifi.starrailexpress.customblock;

import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEvent;
import io.wifi.starrailexpress.customblock.CustomBlockData.BlockEventType;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义方块的行为引擎（服务端）。
 *
 * <p>
 * 事件模型是「一个方块挂多个事件」：右键 / 踩踏事件由原版回调直接触发（零扫描开销），
 * 只有「靠近」事件需要服务端按位置检查。
 *
 * <p>
 * <b>性能设计</b>：
 * <ul>
 * <li><b>只给「有靠近事件的方块」建索引</b>（{@link #PROXIMITY_INDEX}）。右键 / 踩踏事件
 * 完全走 {@code useWithoutItem} / {@code stepOn} 回调，没有方块被扫描也不产生任何 tick 开销；
 * 服务端一个靠近方块都没有时，tick 处理器在第一行就返回。</li>
 * <li>靠近检查每 {@link #PROXIMITY_INTERVAL_TICKS} tick 一次（0.2 秒），只用平方距离比较，
 * 不调用 {@code sqrt}、不分配向量；没有靠近事件的方块不在索引里。</li>
 * <li>冷却与「仅一次」状态按玩家存（{@link #PLAYERS}），玩家离线即回收；
 * 计时用 {@link GameUtils#getTicksFromGameStart(Level)}（时停 / 会议期间暂停，与项目其它冷却一致）。</li>
 * <li>没有任何条件（职业 / 阵营 / 仅游戏中）的事件走一条不含查表的快路径（{@link #conditionsMet}）。</li>
 * </ul>
 */
public final class CustomBlockRuntime {

    /** 靠近检查间隔（tick）。0.2 秒一次，足够跟手且开销可控。 */
    public static final int PROXIMITY_INTERVAL_TICKS = 4;

    /** 维度 → （方块坐标 → 自定义方块 id）。只收录有靠近事件的方块。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, String>> PROXIMITY_INDEX = new ConcurrentHashMap<>();

    /**
     * 维度 → 待按配置校正状态的方块坐标。
     *
     * <p>
     * id 在区块加载（{@code loadAdditional}）与放置（{@code applyImplicitComponents}）里才知道，
     * 而这两个时机都还在区块/方块的构建过程中，此时直接改方块不安全，所以先攒下来，
     * 等下一刻由 {@link #tickProximity} 统一校正（见 {@link #updateIndex}）。
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING_TUNE = new ConcurrentHashMap<>();

    /** 玩家运行时状态（冷却 / 已触发集合 / 当前半径内集合）。 */
    private static final Map<UUID, PlayerState> PLAYERS = new ConcurrentHashMap<>();

    private static boolean initialized = false;

    private CustomBlockRuntime() {
    }

    /** 单个玩家的运行时状态。 */
    private static final class PlayerState {
        /** 右键 / 踩踏 / 靠近事件的冷却记录：key = 方块 id + 事件下标 → 上次触发时刻。 */
        private final Map<String, Long> lastFire = new HashMap<>();
        /** 「每个玩家只触发一次」已触发过的 key 集合。 */
        private final Set<String> firedOnce = new HashSet<>();
        /** 上一次检查时处在半径内的靠近事件 key 集合（用于「进入」判定）。 */
        private final Set<String> inside = new HashSet<>();
        /** 上一次踩到的方块坐标（用于踩踏事件的「进入」判定，避免站在原地每 tick 触发）。 */
        private BlockPos lastStepPos = null;
    }

    // ==================== 初始化 ====================

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(CustomBlockRuntime::tickProximity);
    }

    // ==================== 索引维护（靠近事件用）====================

    /** 方块实体加载 / 数据变化时登记索引（只有带靠近事件的方块会进索引）。 */
    public static void onBlockLoaded(CustomBlockEntity entity) {
        refreshIndex(entity);
        scheduleTune(entity);
    }

    /** 方块实体数据变化时刷新索引与状态（亮度 / 音效桶）。运行中改 id，可以当场校正。 */
    public static void onBlockChanged(CustomBlockEntity entity) {
        refreshIndex(entity);
        tuneState(entity);
    }

    /** 方块实体卸载时摘掉索引。 */
    public static void onBlockRemoved(CustomBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        Map<BlockPos, String> index = PROXIMITY_INDEX.get(level.dimension());
        if (index != null) {
            index.remove(entity.getBlockPos());
        }
    }

    private static void refreshIndex(CustomBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = entity.getBlockPos();
        CustomBlockData data = entity.data();
        boolean tracked = data != null && data.hasProximityEvent();
        Map<BlockPos, String> index = PROXIMITY_INDEX.get(level.dimension());
        if (!tracked) {
            if (index != null) {
                index.remove(pos);
            }
            return;
        }
        if (index == null) {
            index = new ConcurrentHashMap<>();
            PROXIMITY_INDEX.put(level.dimension(), index);
        }
        index.put(pos.immutable(), data.id);
    }

    /**
     * id 确定之后补登记索引，并安排一次状态校正。
     *
     * <p>
     * 给「NBT 载入完成」与「物品组件写入」用：这两条路径都晚于 {@code setLevel}，
     * 那时状态校正因为还不知道 id 而跳过了，所以这里按最终 id 补一次。
     *
     * <p>
     * 校正本身延到下一刻（{@link #PENDING_TUNE}）：这两个时机分别处于区块加载与放置的过程中，
     * 当场 {@code setBlockAndUpdate} 会去改动正在构建的区块 / 方块。
     */
    public static void updateIndex(CustomBlockEntity entity) {
        refreshIndex(entity);
        scheduleTune(entity);
    }

    /** 安排一次「下一刻的状态校正」。 */
    private static void scheduleTune(CustomBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        PENDING_TUNE.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet())
                .add(entity.getBlockPos().immutable());
    }

    /** 把攒下的状态校正做掉（下一刻执行；方块所在区块已卸载时自动跳过）。 */
    private static void applyPendingTunes(MinecraftServer server) {
        if (PENDING_TUNE.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Set<BlockPos> pending = PENDING_TUNE.get(level.dimension());
            if (pending == null || pending.isEmpty()) {
                continue;
            }
            for (BlockPos pos : pending) {
                if (level.getBlockEntity(pos) instanceof CustomBlockEntity entity) {
                    tuneState(entity);
                }
            }
            pending.clear();
        }
    }

    /**
     * 按配置校正方块状态里的亮度与音效桶。
     *
     * <p>
     * 只在服务端做，并且只在值确实不同的时候改方块（{@code setBlockAndUpdate} 会同步给客户端）。
     *
     * <p>
     * <b>id 未知时必须跳过</b>：放置时 {@code setLevel}（→ 这里）早于 {@code applyImplicitComponents}，
     * 区块加载时早于 {@code loadAdditional}，那一刻读到的 data 是 null。若照 null 校正，就会把刚由
     * {@code placedState} 写好的亮度 / 音效桶清成 0 / STONE——表现为「放下时亮一瞬间，随后不亮」。
     */
    private static void tuneState(CustomBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        String id = entity.getCustomBlockId();
        if (id == null || id.isBlank()) {
            return;
        }
        CustomBlockData data = entity.data();
        if (data == null) {
            // id 已知但配置里没有（配置被删了）：不动状态，保住放置时写进去的值
            return;
        }
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof CustomBlock block)) {
            return;
        }
        BlockState tuned = block.tunedState(state, data);
        if (tuned != state) {
            level.setBlockAndUpdate(entity.getBlockPos(), tuned);
        }
    }

    // ==================== 右键 / 踩踏（原版回调，无扫描）====================

    /**
     * 右键事件：按顺序评估该方块配置的所有 RIGHT_CLICK 事件。
     *
     * @return 是否至少触发了一个事件（用于给客户端正确的交互反馈）
     */
    public static boolean fireRightClick(ServerPlayer player, Level level, BlockPos pos, CustomBlockData data,
            List<BlockEvent> events) {
        boolean fired = false;
        for (int i = 0; i < events.size(); i++) {
            BlockEvent event = events.get(i);
            if (event.sneakOnly && !player.isShiftKeyDown()) {
                continue;
            }
            if (!canFire(player, level, data, event, i, "rc")) {
                continue;
            }
            CustomBlockLoader.executeCommands(event.commands, player);
            markFired(player, data, event, i, "rc");
            fired = true;
            if (event.consumeBlock) {
                level.removeBlock(pos, false);
                break;
            }
        }
        return fired;
    }

    /** 踩踏事件：按顺序评估该方块配置的所有 STEP 事件。 */
    public static void fireStep(ServerPlayer player, Level level, BlockPos pos, CustomBlockData data,
            List<BlockEvent> events) {
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        // 「进入」判定：原版 stepOn 在玩家停在方块上时会被反复调用，同一格只当作一次踩踏，
        // 否则无冷却的事件会以每秒 20 次的频率刷指令（要持续触发请用带冷却的靠近事件）。
        boolean firstStepOnBlock = !pos.equals(state.lastStepPos);
        state.lastStepPos = pos.immutable();

        for (int i = 0; i < events.size(); i++) {
            BlockEvent event = events.get(i);
            if (!firstStepOnBlock && event.cooldownTicks <= 0) {
                continue;
            }
            if (!canFire(player, level, data, event, i, "st")) {
                continue;
            }
            CustomBlockLoader.executeCommands(event.commands, player);
            markFired(player, data, event, i, "st");
        }
    }

    // ==================== 靠近（唯一的扫描型事件）====================

    private static void tickProximity(MinecraftServer server) {
        // 状态校正与靠近索引无关，且每次只有零星几个方块，所以放在间隔判断之前、每刻处理
        applyPendingTunes(server);

        if (PROXIMITY_INDEX.isEmpty()) {
            return;
        }
        // 只在间隔 tick 上工作：把 20 次/秒 降到 5 次/秒
        if (server.getTickCount() % PROXIMITY_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, String> index = PROXIMITY_INDEX.get(level.dimension());
            if (index == null || index.isEmpty()) {
                continue;
            }
            if (level.players().isEmpty()) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                checkProximityFor(level, player, index);
            }
        }
    }

    private static void checkProximityFor(ServerLevel level, ServerPlayer player, Map<BlockPos, String> index) {
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        Set<String> stillInside = new HashSet<>();
        long now = GameUtils.getTicksFromGameStart(level);

        for (Map.Entry<BlockPos, String> entry : index.entrySet()) {
            CustomBlockData data = CustomBlockLoader.get(entry.getValue());
            if (data == null) {
                continue;
            }
            List<BlockEvent> events = data.eventsOf(BlockEventType.PROXIMITY);
            if (events.isEmpty()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dy = player.getY() - (pos.getY() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            double distanceSqr = dx * dx + dy * dy + dz * dz;

            for (int i = 0; i < events.size(); i++) {
                BlockEvent event = events.get(i);
                double radius = event.radius;
                if (distanceSqr > radius * radius) {
                    continue;
                }
                String key = data.id + "#" + i + "@" + pos.asLong();
                stillInside.add(key);
                if (event.oncePerPlayer && state.firedOnce.contains(key)) {
                    continue;
                }
                if (event.hasConditions() && !conditionsMet(player, level, event)) {
                    continue;
                }
                if (event.cooldownTicks > 0) {
                    Long last = state.lastFire.get(key);
                    if (last != null && now - last < event.cooldownTicks) {
                        continue;
                    }
                } else if (state.inside.contains(key)) {
                    // 无冷却且不是「只触发一次」：只在「刚进入范围」时触发，避免每 0.2 秒刷一次指令
                    continue;
                }
                CustomBlockLoader.executeCommands(event.commands, player);
                state.lastFire.put(key, now);
                if (event.oncePerPlayer) {
                    state.firedOnce.add(key);
                }
            }
        }
        state.inside.clear();
        state.inside.addAll(stillInside);
    }

    // ==================== 条件 / 冷却 ====================

    /**
     * 事件是否可以触发（类型无关部分：生效条件 + 冷却 + 仅一次）。
     *
     * @param tag 事件类别的短标记（右键 {@code rc} / 踩踏 {@code st}），冷却与一次性按类别分开记，
     *            这样同一个方块上的右键事件与踩踏事件互不影响
     */
    private static boolean canFire(ServerPlayer player, Level level, CustomBlockData data, BlockEvent event,
            int index, String tag) {
        if (event.hasConditions() && !conditionsMet(player, level, event)) {
            return false;
        }
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        String key = data.id + "#" + tag + index;
        if (event.oncePerPlayer && state.firedOnce.contains(key)) {
            return false;
        }
        if (event.cooldownTicks > 0) {
            Long last = state.lastFire.get(key);
            if (last != null && GameUtils.getTicksFromGameStart(level) - last < event.cooldownTicks) {
                return false;
            }
        }
        return true;
    }

    private static void markFired(ServerPlayer player, CustomBlockData data, BlockEvent event, int index, String tag) {
        PlayerState state = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
        String key = data.id + "#" + tag + index;
        if (event.cooldownTicks > 0) {
            state.lastFire.put(key, GameUtils.getTicksFromGameStart(player.level()));
        }
        if (event.oncePerPlayer) {
            state.firedOnce.add(key);
        }
    }

    /** 事件的生效条件：仅游戏进行中 / 职业限定 / 阵营限定。 */
    private static boolean conditionsMet(ServerPlayer player, Level level, BlockEvent event) {
        if (event.gameRunningOnly && !GameUtils.isGameRunning(level)) {
            return false;
        }
        // 空串条目不算条件（见 BlockEvent#hasRoleCondition）
        if (event.hasRoleCondition() && !roleMatches(player, event.requiredRoles)) {
            return false;
        }
        List<RoleTeam> teams = event.teams();
        if (!teams.isEmpty()) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(level);
            var role = gameWorld == null ? null : gameWorld.getRole(player);
            boolean matched = false;
            for (RoleTeam team : teams) {
                if (team.matches(role)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /** 玩家当前职业是否命中列表里的任意一项（复用自定义物品的职业匹配规则）。 */
    private static boolean roleMatches(ServerPlayer player, List<String> roles) {
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            if (CustomItemRuntime.roleMatches(player, role)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 清理 ====================

    /** 玩家离开时清掉他的运行时状态（冷却 / 一次性记录）。 */
    public static void clearPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PLAYERS.remove(playerId);
    }

    /** 服务器停止时清空全部状态。 */
    public static void clearAll() {
        PLAYERS.clear();
        PROXIMITY_INDEX.clear();
    }

    /**
     * 配置重载后重置玩家状态（冷却 / 一次性记录）。
     *
     * <p>
     * 索引不在这里重建：已加载区块的方块实体不会重新触发 {@code onLoad}，
     * 而索引项在读取时都会用最新配置校验（{@link CustomBlockLoader#get(String)} 返回 null 或
     * 不再有靠近事件时会被跳过），因此重载后未加载区块里的方块会在区块下次加载时按新配置登记。
     */
    public static void invalidatePlayerState() {
        PLAYERS.clear();
    }
}
