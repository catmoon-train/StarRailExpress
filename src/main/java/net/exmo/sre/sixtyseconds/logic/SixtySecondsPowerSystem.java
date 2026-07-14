package net.exmo.sre.sixtyseconds.logic;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 电力系统：家里放置<b>发电机</b>（{@code sixty_seconds_generator}），用废料/煤炭作燃料，
 * 每份燃料为全队供电 {@link SixtySecondsBalance#POWER_PER_FUEL_TICKS 90 秒}
 * （{@link SixtySecondsState.TeamData#powerEndTick}，时间戳制不每 tick 同步）。
 * 供电中：电灯（{@code sixty_seconds_lamp}）点亮（照亮黑暗角落防低语怪）、
 * 需电配方（{@link SixtySecondsRecipes.Recipe#needsPower()}）可用。
 */
public final class SixtySecondsPowerSystem {
    /** level → (发电机/电灯位置 → teamId)，放置时登记、拆除时移除（不落盘，随局重建）。 */
    private static final Map<ServerLevel, Map<BlockPos, Integer>> GENERATORS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, Integer>> LAMPS = new WeakHashMap<>();

    private SixtySecondsPowerSystem() {
    }

    public static boolean isPowered(ServerLevel level, SixtySecondsState.TeamData team) {
        return team != null && level.getGameTime() < team.powerEndTick;
    }

    /**
     * 60s：小游戏镶板需要本队供电才能使用（发电机断电=镶板停机）。
     * 非 60s 模式 / 未入队恒 false（不影响其他模式的镶板）。
     * 由 {@code MinigameQuestBlock} 的服务端交互门控调用。
     */
    public static boolean minigameBlockedByPower(net.minecraft.server.level.ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || !net.exmo.sre.sixtyseconds.SixtySecondsMod.isActive(level)) {
            return false;
        }
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(
                net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId);
        return team != null && !isPowered(level, team);
    }

    /** 加一份燃料：续期供电时间并立即点亮本队发电机/电灯。 */
    public static void addFuel(ServerLevel level, SixtySecondsState.TeamData team) {
        long base = Math.max(level.getGameTime(), team.powerEndTick);
        team.powerEndTick = base + SixtySecondsBalance.POWER_PER_FUEL_TICKS;
        refresh(level, team.teamId, true);
    }

    public static void registerGenerator(ServerLevel level, BlockPos pos, int teamId) {
        GENERATORS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), teamId);
    }

    public static void registerLamp(ServerLevel level, BlockPos pos, int teamId) {
        LAMPS.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos.immutable(), teamId);
        SixtySecondsState.TeamData team = SixtySecondsState.get(level).teams.get(teamId);
        if (isPowered(level, team)) {
            setLit(level, pos, true);
        }
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Integer> generators = GENERATORS.get(level);
        if (generators != null) {
            generators.remove(pos);
        }
        Map<BlockPos, Integer> lamps = LAMPS.get(level);
        if (lamps != null) {
            lamps.remove(pos);
        }
    }

    /** 每秒检查断电边沿：供电刚过期的队伍熄灭其发电机/电灯。 */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 20 != 0) {
            return;
        }
        for (SixtySecondsState.TeamData team : SixtySecondsState.get(level).teams.values()) {
            if (team.powerEndTick > 0 && now >= team.powerEndTick && now - 20 < team.powerEndTick) {
                refresh(level, team.teamId, false);
            }
        }
    }

    /** 同步本队所有发电机/电灯的 LIT 状态。 */
    private static void refresh(ServerLevel level, int teamId, boolean powered) {
        Map<BlockPos, Integer> generators = GENERATORS.get(level);
        if (generators != null) {
            for (Map.Entry<BlockPos, Integer> entry : generators.entrySet()) {
                if (entry.getValue() == teamId) {
                    setLit(level, entry.getKey(), powered);
                }
            }
        }
        Map<BlockPos, Integer> lamps = LAMPS.get(level);
        if (lamps != null) {
            for (Map.Entry<BlockPos, Integer> entry : lamps.entrySet()) {
                if (entry.getValue() == teamId) {
                    setLit(level, entry.getKey(), powered);
                }
            }
        }
    }

    private static void setLit(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
        }
    }

    public static void reset(ServerLevel level) {
        GENERATORS.remove(level);
        LAMPS.remove(level);
    }
}
