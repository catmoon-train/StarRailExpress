package net.exmo.sre.sixtyseconds.state;

import net.exmo.sre.sixtyseconds.SixtySecondsPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 末日60秒模式的世界级运行态（按 {@link ServerLevel} 存）。
 * 计时用 {@code level.getGameTime()} 时间戳（{@code phaseEndTick}），不每 tick 递减、不每 tick 同步。
 */
public final class SixtySecondsState {
    private static final Map<ServerLevel, Data> STATES = new WeakHashMap<>();

    private SixtySecondsState() {
    }

    public static Data get(ServerLevel level) {
        return STATES.computeIfAbsent(level, ignored -> new Data());
    }

    public static void reset(ServerLevel level) {
        STATES.put(level, new Data());
    }

    /** 每队（家庭）的运行态。 */
    public static final class TeamData {
        public final int teamId;
        public final List<UUID> members = new ArrayList<>();
        /** 准备阶段右键门记录进「库存」的物资，准备结束放入避难所箱子。 */
        public final List<net.minecraft.world.item.ItemStack> storedSupplies = new ArrayList<>();
        /** 本队住宅 / 避难所 / 搜索区出生点（已叠加网格偏移的绝对坐标）。 */
        public BlockPos residentialSpawn;
        public BlockPos shelterSpawn;
        public BlockPos searchZoneSpawn;
        /** 本队搜索区限制盒（已叠加网格偏移）。 */
        public AABB searchZoneBox;
        /** 本队住宅 / 避难所范围盒（已叠加网格偏移，用于「在家降速」判定）。 */
        public AABB residentialBox;
        public AABB shelterBox;

        public TeamData(int teamId) {
            this.teamId = teamId;
        }
    }

    public static final class Data {
        public SixtySecondsPhase phase = SixtySecondsPhase.INACTIVE;
        public int dayNumber = 0;
        public long phaseEndTick = 0L;
        /** teamId → TeamData（保持插入顺序，用于网格布局的 index）。 */
        public final Map<Integer, TeamData> teams = new LinkedHashMap<>();
        /** 本局已觉醒（分配过）的职业 ID 字符串——保证每职业只出现一次。 */
        public final java.util.Set<String> usedAwakenRoles = new java.util.HashSet<>();
    }
}
