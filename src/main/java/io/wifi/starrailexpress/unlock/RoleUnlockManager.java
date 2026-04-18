package io.wifi.starrailexpress.unlock;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import pro.fazeclan.river.stupid_express.StupidExpressConfig;

import java.util.*;

/**
 * @deprecated
 *             职业解锁管理器
 *             <p>
 *             定义所有职业的解锁阈值（全局游玩场次），并提供统一的解锁判断接口。
 *             不在阈值表内的职业默认始终可用（阈值视为 0）。
 *             </p>
 *             <p>
 *             服务端读取 {@link RoleUnlockStorage} 进行持久化判断；
 *             客户端通过 {@code updateClientData()} 方法接收服务端同步的数据（用于 GUI 展示）。
 *             </p>
 */
@Deprecated
public class RoleUnlockManager {

    private static final RoleUnlockManager INSTANCE = new RoleUnlockManager();

    // ─── 解锁阈值表（全局游玩场次 → 解锁对应职业）────────────────────────────
    /** 职业 ID → 所需全局场次（不在表内=始终可用） */
    public static final LinkedHashMap<ResourceLocation, Integer> UNLOCK_THRESHOLDS = new LinkedHashMap<>();

    static {
        // ── 乘客阵营 ──────────────────────────────────────────────────────
        UNLOCK_THRESHOLDS.put(RedHouseRoles.BAKA_ID, 3);
        UNLOCK_THRESHOLDS.put(ModRoles.JESTER_ID, 3);
        UNLOCK_THRESHOLDS.put(ModRoles.CONDUCTOR_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.DOCTOR_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.ATTENDANT_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.BROADCASTER_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.ATHLETE_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.RESCUER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.WAYFARER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.ELF_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.GHOST_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.PATROLLER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.ENGINEER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.BARTENDER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.NOISEMAKER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.BOXER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.POSTMAN_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.DETECTIVE_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.SINGER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.WRITER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.FIREFIGHTER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.CORONER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.CHEF_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.SLIPPERY_GHOST_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.VOODOO_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.WIND_YAOSE_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.TELEGRAPHER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.ACCOUNTANT_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.DIVER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.PHOTOGRAPHER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.SUPERSTAR_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.AVENGER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.RECALLER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.GLITCH_ROBOT_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.VETERAN_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.PSYCHOLOGIST_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.ALCHEMIST_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.MAGICIAN_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.AWESOME_BINGLUS_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.BETTER_VIGILANTE_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.BASEBALL_PLAYER_ID, 1);
        UNLOCK_THRESHOLDS.put(ModRoles.CREEPER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.CLOCKMAKER_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.SWAST_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.MARTIAL_ARTS_INSTRUCTOR_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.SEA_KING_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.BEST_VIGILANTE_ID, 25);

        // ── 杀手阵营 ──────────────────────────────────────────────────────
        UNLOCK_THRESHOLDS.put(ModRoles.LOCKSMITH_ID, 5);
        UNLOCK_THRESHOLDS.put(RedHouseRoles.PACHURI_ID, 5);
        UNLOCK_THRESHOLDS.put(ModRoles.SWAPPER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.MORPHLING_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.PHANTOM_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.POISONER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.SHOOTING_FRENZY_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.GAMBLER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.EXECUTIONER_ID, 12);
        UNLOCK_THRESHOLDS.put(RedHouseRoles.MAID_SAKUYA_ID, 12);
        UNLOCK_THRESHOLDS.put(RedHouseRoles.HOAN_MEIRIN_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.EXAMPLER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.CLEANER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.TRAPPER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.BOMBER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.WATCHER_ID, 18);
        UNLOCK_THRESHOLDS.put(ModRoles.BANDIT_ID, 18);
        UNLOCK_THRESHOLDS.put(ModRoles.CONSPIRATOR_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.MANIPULATOR_ID, 25);
        UNLOCK_THRESHOLDS.put(ModRoles.BLOOD_FEUDIST_ID, 25);

        // ── 中立阵营 ──────────────────────────────────────────────────────
        UNLOCK_THRESHOLDS.put(ModRoles.FORTUNETELLER_ID, 8);
        UNLOCK_THRESHOLDS.put(ModRoles.ADMIRER_ID, 10);
        UNLOCK_THRESHOLDS.put(ModRoles.STALKER_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.THIEF_ID, 12);
        UNLOCK_THRESHOLDS.put(ModRoles.MONITOR_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.RECORDER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.VULTURE_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.CANDLE_BEARER_ID, 15);
        UNLOCK_THRESHOLDS.put(ModRoles.OLDMAN_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.NIAN_SHOU_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.COMMANDER_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.PUPPETEER_ID, 20);
        UNLOCK_THRESHOLDS.put(ModRoles.JOJO_ID, 30);
    }

    // ─── 客户端状态（从网络包同步）────────────────────────────────────────
    /** 客户端缓存的全局场次（由服务端同步） */
    private int clientGlobalGamesPlayed = 0;
    /** 客户端缓存的强制解锁集合（由服务端同步） */
    private final Set<String> clientForceUnlocked = new HashSet<>();

    private RoleUnlockManager() {
    }

    public static RoleUnlockManager getInstance() {
        return INSTANCE;
    }

    /** 配置开关：关闭时视为所有职业已解锁。 */
    public boolean isRoleUnlockEnabled() {
        try {
            return StupidExpressConfig.getInstance().rolesSection.roleUnlockSection.enableRoleUnlockSystem;
        } catch (Exception ignored) {
            return true;
        }
    }

    // ─── 核心判断 ────────────────────────────────────────────────────────────

    /**
     * 判断指定职业是否已解锁。
     * <ul>
     * <li>服务端：从 {@link RoleUnlockStorage} 读取真实数据</li>
     * <li>客户端：使用上次从服务端同步的数据（仅用于 GUI 展示）</li>
     * </ul>
     */
    public boolean isRoleUnlocked(ResourceLocation roleId) {
        if (!isRoleUnlockEnabled()) {
            return true;
        }

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            // 客户端使用同步来的缓存
            if (clientForceUnlocked.contains(roleId.toString()))
                return true;
            Integer threshold = UNLOCK_THRESHOLDS.get(roleId);
            if (threshold == null)
                return true;
            return clientGlobalGamesPlayed >= threshold;
        }
        // 服务端：读 Storage
        RoleUnlockStorage storage = RoleUnlockStorage.getInstance();
        if (storage.isForceUnlocked(roleId))
            return true;
        Integer threshold = UNLOCK_THRESHOLDS.get(roleId);
        if (threshold == null)
            return true;
        return storage.getGlobalGamesPlayed() >= threshold;
    }

    /**
     * 获取职业的解锁所需场次（0 = 始终可用）。
     */
    public int getThreshold(ResourceLocation roleId) {
        return UNLOCK_THRESHOLDS.getOrDefault(roleId, 0);
    }

    /**
     * 一键强制解锁所有已定义阈值的职业（服务端调用）。
     */
    public void unlockAll() {
        RoleUnlockStorage.getInstance().forceUnlockAll(new ArrayList<>(UNLOCK_THRESHOLDS.keySet()));
    }

    /**
     * 获取所有禁用的职业（未解锁的职业）列表。
     * <ul>
     * <li>服务端：基于 {@link RoleUnlockStorage} 的真实数据判断</li>
     * <li>客户端：基于从服务端同步的缓存数据判断</li>
     * </ul>
     *
     * @return 未解锁职业的 ResourceLocation 列表
     */
    public List<ResourceLocation> getDisabledRoles() {
        List<ResourceLocation> disabled = new ArrayList<>();
        for (ResourceLocation roleId : UNLOCK_THRESHOLDS.keySet()) {
            if (!isRoleUnlocked(roleId)) {
                disabled.add(roleId);
            }
        }
        return disabled;
    }

    /**
     * Compute roles unlocked by global game count transition [before -> after].
     */
    public List<ResourceLocation> getNewlyUnlockedRoles(int gamesBefore, int gamesAfter,
            Collection<String> forceUnlocked) {
        if (!isRoleUnlockEnabled() || gamesAfter <= gamesBefore) {
            return List.of();
        }
        Set<String> forceSet = new HashSet<>(forceUnlocked);
        List<ResourceLocation> unlocked = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : UNLOCK_THRESHOLDS.entrySet()) {
            ResourceLocation roleId = entry.getKey();
            int threshold = entry.getValue();
            if (forceSet.contains(roleId.toString())) {
                continue;
            }
            if (threshold > gamesBefore && threshold <= gamesAfter) {
                unlocked.add(roleId);
            }
        }
        unlocked.sort(Comparator.comparingInt(id -> UNLOCK_THRESHOLDS.getOrDefault(id, Integer.MAX_VALUE)));
        return unlocked;
    }

    // ─── 客户端数据同步 ──────────────────────────────────────────────────────

    /**
     * 客户端接收到服务端同步包时调用，更新本地缓存数据。
     */
    public void updateClientData(int globalGamesPlayed, Collection<String> forceUnlocked) {
        this.clientGlobalGamesPlayed = globalGamesPlayed;
        this.clientForceUnlocked.clear();
        this.clientForceUnlocked.addAll(forceUnlocked);
    }

    /**
     * 客户端用：返回当前已知的全局场次（同步来的值）。
     */
    public int getClientGlobalGamesPlayed() {
        return clientGlobalGamesPlayed;
    }

    // ─── GUI 数据模型 ─────────────────────────────────────────────────────────

    /**
     * 一条职业解锁进度记录（用于 GUI 展示）。
     */
    public record RoleUnlockEntry(
            ResourceLocation roleId,
            int threshold,
            int currentGames,
            boolean isUnlocked,
            int roleColor) {
    }

    /**
     * 返回所有有阈值的职业的解锁进度列表（客户端调用）。
     * 按 已解锁→未解锁、再按阈值升序 排列。
     */
    public List<RoleUnlockEntry> buildClientEntries() {
        int games = clientGlobalGamesPlayed;
        List<RoleUnlockEntry> list = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Integer> entry : UNLOCK_THRESHOLDS.entrySet()) {
            ResourceLocation id = entry.getKey();
            int threshold = entry.getValue();
            boolean unlocked = isRoleUnlocked(id);
            int color = getRoleColor(id);
            list.add(new RoleUnlockEntry(id, threshold, games, unlocked, color));
        }
        list.sort(Comparator.<RoleUnlockEntry, Boolean>comparing(e -> !e.isUnlocked())
                .thenComparingInt(RoleUnlockEntry::threshold)
                .thenComparing(e -> e.roleId().toString()));
        return list;
    }

    /** 从 TMMRoles 注册表中查询职业颜色，不存在时返回灰色 */
    private static int getRoleColor(ResourceLocation id) {
        SRERole role = TMMRoles.ROLES.get(id);
        return role != null ? role.getColor() : 0xAAAAAA;
    }
}
