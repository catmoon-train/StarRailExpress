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
        /**
         * 本队在共享搜索区里的「回家门」（绝对坐标；来自搜索区内的门绑定按队轮转分配）。
         * 非空时只有走到这扇门才能「返回住所」；为空则任意门可回（旧行为）。
         */
        public BlockPos returnDoorPos;
        /**
         * 本队「避难所门坐标 → 专属探索区」映射（门坐标/盒/出生点均已叠加网格偏移）。
         * 由绑定工具生成的 {@code searchDoorBindings} 克隆而来；未绑定的门回退到 {@link #searchZoneSpawn}/{@link #searchZoneBox}。
         */
        public final Map<BlockPos, SearchLink> searchDoors = new java.util.HashMap<>();
        /** 本队住宅 / 避难所范围盒（已叠加网格偏移，用于「在家降速」判定）。 */
        public AABB residentialBox;
        public AABB shelterBox;

        // ── 科技树 / 电力（SixtySecondsTechTree / SixtySecondsPowerSystem）───
        /** 本队已解锁的科技 id。 */
        public final java.util.Set<String> unlockedTech = new java.util.HashSet<>();
        /** 供电截止 gameTime（发电机烧燃料续期）；小于当前时间即断电。 */
        public long powerEndTick = 0L;

        // ── 家门攻防（SixtySecondsDefenseSystem）────────────────────────────
        /** 家门耐久；夜袭怪物冲击扣减，木板/铁锭加固恢复并提升上限。 */
        public int doorHp = 100;
        public int doorMaxHp = 100;
        /** 门被攻破：全队视为「室外状态」（消耗加倍、无法睡觉回血），修复至 >0 解除。 */
        public boolean doorBroken = false;
        /** 门等级 1..3：闯入者需要不低于此等级的撬棍/开锁器。 */
        public int doorLevel = 1;
        /** 铁锭加固累计次数（满 3 次门升一级）。 */
        public int ironReinforceCount = 0;
        /** 本队避难所门（夜袭目标），首次夜袭时扫描缓存。 */
        public BlockPos doorPos;
        /**
         * 今晚夜袭怪实际冲击的门（运行时，随晚重算）：优先=避难所<b>物理门</b>（怪刷在门外、玩家在屋内可见可防），
         * 门外无落点时退回探索区锚点门（旧行为）。null=按 {@code assaultAnchor} 兜底。
         */
        public BlockPos assaultDoorPos;
        /** 警报器：今晚夜袭者 -1（每晚一次，换日重置）。 */
        public boolean alarmTonight = false;
        /** 诱饵：今晚本队一半夜袭者被引向随机别队（每晚一次，换日重置）。 */
        public boolean lureTonight = false;
        /** 门锁有效截止 gameTime（挂锁后 6 分钟内阻断撬棍强闯；过期自然失效）。 */
        public long doorLockEndTick = 0L;
        /** 门陷阱有效截止 gameTime（6 分钟内开锁器入室触发警报并消耗；过期自然失效）。 */
        public long doorTrapEndTick = 0L;

        public boolean doorLockActive(long now) {
            return now < doorLockEndTick;
        }

        public boolean doorTrapActive(long now) {
            return now < doorTrapEndTick;
        }

        // ── 每日事件日级修正（键 → 倍率；换日清空）──────────────────────
        /** 事件施加的日级修正：键=修正名，值=倍率（1.0=不变，>1=恶化，<1=改善）。 */
        public final java.util.Map<String, Double> dailyModifiers = new java.util.HashMap<>();

        /** 读取修正倍率，无记录返回 1.0。 */
        public double modifier(String key) {
            return dailyModifiers.getOrDefault(key, 1.0);
        }

        /** 换日时清空所有日级修正（保留持久标记如 sisterOutside）。 */
        public void clearDailyModifiers() {
            dailyModifiers.clear();
        }

        // ── 妹妹外出事件持久标记 ──────────────────────────────────────
        /** 该队的妹妹是否已外出（标记后换日存活则变异）。换日不清空，仅在外出被阻止/变怪后重置。 */
        public boolean sisterOutside = false;
        /** 外出妹妹的玩家 UUID（仅 sisterOutside=true 时有效），换日检测存活用。 */
        public java.util.UUID sisterUUID = null;

        public TeamData(int teamId) {
            this.teamId = teamId;
        }

        /** 一扇门对应的独立探索区：出生点 + 限制盒（均绝对坐标）。 */
        public record SearchLink(BlockPos spawn, AABB box) {
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
        /** 上次广播过的日内阶段（0=清晨 1=白天 2=晚上 3=睡觉，-1=未初始化），用于子相位切换提示。 */
        public int lastDayStage = -1;
    }
}
