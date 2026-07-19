package net.exmo.sre.sixtyseconds.logic;

import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 避难所电脑（{@code MinigameQuestBlockEntity}）小游戏轮换：
 * <b>每 1.5 分钟</b>（{@link #ROTATE_INTERVAL}）重新扫描每支队伍避难所内的任务点方块，把它换成一个
 * <b>不同</b>的小游戏并同步客户端；同时清掉这些镶板的复用冷却。
 * 刷新提示只发给身处避难所内的玩家（内含 50s 限时说明）。
 * <p>
 * 奖励门控统一走 {@link #tryReward}（由 {@code MinigameQuestServerNetwork} 在完成处调用）：
 * <ol>
 *   <li>须在本轮刷新后 {@link #SUCCESS_WINDOW 50s} 内完成；</li>
 *   <li><b>每个刷新周期内每支队伍只能完成一次</b>——防止全队轮流刷同一块镶板刷代币。</li>
 * </ol>
 */
public final class SixtySecondsMinigameRotation {
    public static final int ROTATE_INTERVAL = 20 * 90;   // 每 1.5 分钟刷新一次
    public static final int SUCCESS_WINDOW = 20 * 50;    // 50s 完成窗口（原 20s → 加强至 50s）

    private static final Map<ServerLevel, List<BlockPos>> BLOCKS = new WeakHashMap<>();
    /** 每个任务点上次轮换的 gameTime，用于 20s 奖励门控。 */
    private static final Map<ServerLevel, Map<BlockPos, Long>> ROTATION_TIME = new WeakHashMap<>();
    /** level →（队伍 id → 该队最近一次完成所属的刷新周期序号）：实现「每个刷新周期每队只能完成一次」。 */
    private static final Map<ServerLevel, Map<Integer, Long>> TEAM_DONE_PERIOD = new WeakHashMap<>();

    private SixtySecondsMinigameRotation() {
    }

    /** 当前刷新周期序号（每 {@link #ROTATE_INTERVAL} 递增 1；轮换正是在周期边界发生）。 */
    private static long periodOf(long gameTime) {
        return gameTime / ROTATE_INTERVAL;
    }

    /**
     * 完成小游戏时的统一奖励门控（{@code MinigameQuestServerNetwork} 完成处调用一次）：
     * 通过则<b>记账本队本周期已完成</b>并返回 true；否则按具体原因给玩家发提示并返回 false。
     * <p>⚠️ 有副作用（成功即消耗本队本周期的唯一名额），每次完成只能调用一次。
     */
    public static boolean tryReward(ServerLevel level, BlockPos pos, ServerPlayer player) {
        // 1) 20s 完成窗口
        if (!canReward(level, pos)) {
            player.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.minigame_too_late"), true);
            return false;
        }
        // 2) 每个刷新周期每队只有一次名额（无队伍的旁观/管理员不占名额、也不受限）
        int teamId = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId;
        if (teamId >= 0) {
            Map<Integer, Long> done = TEAM_DONE_PERIOD.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
            long period = periodOf(level.getGameTime());
            Long last = done.get(teamId);
            if (last != null && last == period) {
                player.displayClientMessage(Component.translatable(
                        "message.noellesroles.sixty_seconds.minigame_team_done"), true);
                return false;
            }
            done.put(teamId, period);
        }
        return true;
    }

    /**
     * 该任务点当前是否处于「20s 完成窗口」内。
     * 非轮换任务点（未登记）一律放行。
     */
    private static boolean canReward(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Long> times = ROTATION_TIME.get(level);
        if (times == null) {
            return true;
        }
        Long rotated = times.get(pos.immutable());
        if (rotated == null) {
            return true;
        }
        return level.getGameTime() - rotated <= SUCCESS_WINDOW;
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 20 == 0) {
            ensureTasks(level);
        }
        if (now % ROTATE_INTERVAL != 0) {
            return;
        }
        // 每次轮换都重新扫描：一次性缓存会漏掉扫描时区块未载入/之后放置的镶板
        // （「避难所镶板不刷新」的根因之一），扫描范围小、每 1.5 分钟一次开销可忽略
        List<BlockPos> blocks = scan(level);
        BLOCKS.put(level, blocks);
        if (blocks.isEmpty()) {
            return;
        }
        List<QuestMinigame> all = QuestMinigames.getAll();
        if (all.isEmpty()) {
            return;
        }
        List<BlockPos> rotated = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (!(level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity be)) {
                continue;
            }
            String current = be.getMinigameId();
            String next = current;
            for (int i = 0; i < 8 && (next == null || next.equals(current)); i++) {
                next = all.get(level.getRandom().nextInt(all.size())).id();
            }
            be.setMinigameId(next);
            be.sync(); // setMinigameId 只 setChanged 不同步；金色任务透视在客户端按 BE 的 id 匹配，必须推送
            ROTATION_TIME.computeIfAbsent(level, ignored -> new java.util.HashMap<>()).put(pos.immutable(), now);
            rotated.add(pos.immutable());
        }
        if (rotated.isEmpty()) {
            return;
        }
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        Component message = Component.translatable("message.noellesroles.sixty_seconds.minigame_refresh",
                SUCCESS_WINDOW / 20).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            var comp = io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player);
            boolean changed = false;
            // 清掉被轮换镶板的复用冷却：新一轮 20s 任务应立即可玩，否则上轮玩过的镶板一直提示冷却中
            for (BlockPos pos : rotated) {
                changed |= comp.blockCooldownUntil.remove(pos.asLong()) != null;
            }
            // 任务待办/目标由 ensureTasks 每秒维护（目标恒为空=任意镶板可玩），此处无需对齐
            if (changed) {
                comp.sync();
            }
            // 刷新提示只发给身处避难所内的玩家
            if (isInAnyShelter(player, data)) {
                player.displayClientMessage(message, false);
            }
        }
    }

    /**
     * 60s 的电脑不走原版任务派发：原派发链依赖地图设置 {@code minigameQuestEnabled}（默认关）
     * 且默认配置 {@code minigameTaskRotationMode} 会把小游戏任务并入 Mood 任务轮换——而 60s
     * {@code hasMood()=false}，两条路都断 → 玩家永远没有待办任务，右键镶板被
     * 「当前没有小游戏任务」拒绝（「镶板不刷新小游戏」的根因）。
     * 这里每秒把每名存活玩家的待办数补到 1、目标小游戏置空（=任意镶板可玩，也避免随机指派
     * 与轮换后的镶板「类型不符」）；防刷仍由 每镶板复用冷却 + 20s 完成窗口（{@link #canReward}）承担。
     */
    private static void ensureTasks(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            var comp = io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(player);
            boolean changed = false;
            if (comp.pendingMinigameTasks < 1) {
                comp.pendingMinigameTasks = 1;
                changed = true;
            }
            if (comp.targetMinigameId != null) {
                comp.targetMinigameId = null;
                changed = true;
            }
            if (changed) {
                comp.sync();
            }
        }
    }

    /** 玩家是否身处任意队伍的避难所盒内。 */
    private static boolean isInAnyShelter(ServerPlayer player, SixtySecondsState.Data data) {
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            if (team.shelterBox != null
                    && team.shelterBox.contains(player.getX(), player.getY(), player.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** 扫描各队避难所范围盒内的任务点方块，缓存其坐标。 */
    private static List<BlockPos> scan(ServerLevel level) {
        List<BlockPos> found = new ArrayList<>();
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            AABB box = team.shelterBox;
            if (box == null) {
                continue;
            }
            BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
            BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                // 破坏任务触发点不参与轮换（那是杀手技能的配置，换掉会破坏其语义）
                if (level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity be
                        && !be.isSabotageTrigger()) {
                    found.add(pos.immutable());
                }
            }
        }
        return found;
    }

    public static void reset(ServerLevel level) {
        BLOCKS.remove(level);
        ROTATION_TIME.remove(level);
        TEAM_DONE_PERIOD.remove(level);
    }
}
