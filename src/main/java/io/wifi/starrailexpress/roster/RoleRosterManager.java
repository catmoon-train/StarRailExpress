package io.wifi.starrailexpress.roster;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.network.RoleRosterSyncPayload;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 职业轮换系统的服务端核心：维护一份服务器全局的职业名单，并负责
 * <ul>
 *     <li>从本地文件 / MySQL 数据库加载与持久化；</li>
 *     <li>把名单广播给所有客户端（含新加入的玩家）；</li>
 *     <li>名单启用时，由 {@code RoleAssignmentPool} 在建池时读取本配置，接管职业的启用/禁用与数量。</li>
 * </ul>
 * 数据库按玩家 UUID 分键存储，这里使用一个固定的“配置 UUID”表示服务器全局配置。
 */
public final class RoleRosterManager {
    /** 在 MySQL 玩家数据表中代表“服务器全局配置”的固定 UUID。 */
    public static final UUID CONFIG_UUID = new UUID(0L, 0x5_2_0_5_7_E_4_1L);
    public static final String PART = "role_roster";

    private static final Gson GSON = new GsonBuilder().create();
    private static final long SAVE_TIMEOUT_MS = 4_000L;
    private static final Path LOCAL_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("sre_role_roster.json");

    private static volatile RoleRosterState state = RoleRosterState.createDefault();
    private static volatile MinecraftServer server;

    private RoleRosterManager() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(RoleRosterManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> flushBlocking());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> sendTo(handler.getPlayer()));
    }

    public static RoleRosterState getState() {
        return state;
    }

    public static boolean isEnabled() {
        return state.enabled;
    }

    // ------------------------------------------------------------------
    // 生命周期 / 加载
    // ------------------------------------------------------------------

    private static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        // 先读本地文件（即使数据库不可用也有配置可用）
        RoleRosterState local = readLocalFile();
        if (local != null) {
            state = local.normalized();
        }
        broadcast();
        // 再尝试从数据库覆盖（数据库版本更新时为准）
        if (!isDatabaseEnabled()) {
            return;
        }
        MysqlPlayerDataStore.loadBatchAsync(CONFIG_UUID, List.of(PART))
                .whenComplete((records, throwable) -> {
                    MinecraftServer srv = server;
                    if (srv == null) {
                        return;
                    }
                    srv.execute(() -> {
                        if (throwable != null) {
                            SRE.LOGGER.warn("[RoleRoster] 从数据库加载职业轮换配置失败", throwable);
                            return;
                        }
                        MysqlPlayerDataStore.SyncRecord record = records.get(PART);
                        if (record == null || record.payload() == null || record.payload().isBlank()) {
                            return;
                        }
                        RoleRosterState remote = fromJson(record.payload());
                        if (remote.version >= state.version) {
                            state = remote.normalized();
                            writeLocalFile();
                            broadcast();
                        }
                    });
                });
    }

    // ------------------------------------------------------------------
    // 修改入口（均在服务端线程调用）
    // ------------------------------------------------------------------

    /** 用完整名单覆盖当前配置（管理员手动编辑）。 */
    public static void setFromJson(String json) {
        RoleRosterState incoming = fromJson(json);
        boolean enabled = incoming.enabled;
        incoming.normalized();
        state.roleCounts = incoming.roleCounts;
        state.modifierCounts = incoming.modifierCounts;
        state.enabled = enabled;
        afterMutated();
    }

    public static void setEnabled(boolean enabled) {
        if (state.enabled == enabled) {
            return;
        }
        state.enabled = enabled;
        afterMutated();
    }

    public static void setCount(String roleId, int count) {
        if (count <= 0) {
            state.roleCounts.remove(roleId);
        } else {
            state.roleCounts.put(roleId, count);
        }
        afterMutated();
    }

    public static void clear() {
        state.roleCounts.clear();
        state.modifierCounts.clear();
        afterMutated();
    }

    /** 随机抽选生成一份名单。targetPlayers 用于决定基础平民数量。 */
    public static void randomize(int targetPlayers) {
        Random random = new Random();
        state.roleCounts.clear();
        boolean hasKiller = false;
        for (SRERole role : Noellesroles.getAllRolesSorted()) {
            if (!isRosterEligible(role)) {
                continue;
            }
            String id = role.identifier().toString();
            if (role == TMMRoles.CIVILIAN) {
                state.roleCounts.put(id, Math.max(2, targetPlayers));
                continue;
            }
            float chance = role.canUseKiller() ? 0.5f : (role.isInnocent() ? 0.4f : 0.5f);
            if (random.nextFloat() < chance) {
                state.roleCounts.put(id, 1);
                if (role.canUseKiller()) {
                    hasKiller = true;
                }
            }
        }
        // 至少保证存在一个杀手职业
        if (!hasKiller) {
            state.roleCounts.put(TMMRoles.KILLER.identifier().toString(), 1);
        }
        state.roleCounts.putIfAbsent(TMMRoles.CIVILIAN.identifier().toString(), Math.max(2, targetPlayers));
        afterMutated();
    }

    private static void afterMutated() {
        state.version = Math.max(System.currentTimeMillis(), state.version + 1L);
        state.normalized();
        writeLocalFile();
        saveToDatabase();
        broadcast();
    }

    // ------------------------------------------------------------------
    // 职业分配接入：名单的启用/禁用与数量由 RoleAssignmentPool 在建池时直接读取
    // （见 RoleAssignmentPool#createInternal），此处不再改动全局状态。
    // ------------------------------------------------------------------

    private static boolean isRosterEligible(SRERole role) {
        try {
            // 与 AbstractRoleRosterScreen.isRosterEligible 保持一致：不要用 role.canBeRandomed() 过滤，
            // 该字段由 setCanBeRandomedByOtherRoles 设置，含义是“能否进入其他职业的随机池”，与名单无关，
            // 否则阿蒙、亡灵之主等调用了 setCanBeRandomedByOtherRoles(false) 的职业会被错误排除。
            return !role.isOtherModeRole() && role.getOccupiedRoleCount() <= 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 网络同步
    // ------------------------------------------------------------------

    private static void broadcast() {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        String json = toJson(state);
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new RoleRosterSyncPayload(json));
        }
    }

    private static void sendTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new RoleRosterSyncPayload(toJson(state)));
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private static boolean isDatabaseEnabled() {
        return SREConfig.instance().mysqlPlayerSyncEnabled && MysqlPlayerDataStore.isAvailable();
    }

    private static void saveToDatabase() {
        if (!isDatabaseEnabled()) {
            return;
        }
        long updatedAt = Math.max(1L, state.version);
        MysqlPlayerDataStore.saveBatchAsync(CONFIG_UUID, Map.of(PART, toJson(state)), updatedAt)
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        SRE.LOGGER.warn("[RoleRoster] 保存职业轮换配置到数据库失败", throwable);
                    }
                });
    }

    private static void flushBlocking() {
        writeLocalFile();
        if (!isDatabaseEnabled()) {
            return;
        }
        MysqlPlayerDataStore.saveBatchBlocking(CONFIG_UUID, Map.of(PART, toJson(state)),
                Math.max(1L, state.version), SAVE_TIMEOUT_MS);
    }

    private static RoleRosterState readLocalFile() {
        try {
            if (!Files.exists(LOCAL_FILE)) {
                return null;
            }
            String json = Files.readString(LOCAL_FILE, StandardCharsets.UTF_8);
            return fromJson(json);
        } catch (Exception exception) {
            SRE.LOGGER.warn("[RoleRoster] 读取本地职业轮换配置失败", exception);
            return null;
        }
    }

    private static void writeLocalFile() {
        try {
            Files.createDirectories(LOCAL_FILE.getParent());
            Files.writeString(LOCAL_FILE, toJson(state), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            SRE.LOGGER.warn("[RoleRoster] 写入本地职业轮换配置失败", exception);
        }
    }

    private static RoleRosterState fromJson(String json) {
        try {
            RoleRosterState parsed = GSON.fromJson(json, RoleRosterState.class);
            return parsed == null ? RoleRosterState.createDefault() : parsed.normalized();
        } catch (RuntimeException exception) {
            return RoleRosterState.createDefault();
        }
    }

    private static String toJson(RoleRosterState value) {
        return GSON.toJson(value);
    }
}
