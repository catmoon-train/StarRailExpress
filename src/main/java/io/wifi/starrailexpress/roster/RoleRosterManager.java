package io.wifi.starrailexpress.roster;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.network.RoleRosterSyncPayload;
import net.exmo.sre.sync.MysqlPlayerDataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.agmas.harpymodloader.modifiers.SREModifier;

/**
 * 职业轮换系统的服务端核心：维护一份服务器全局的职业名单，并负责
 * <ul>
 * <li>从本地文件 / MySQL 数据库加载与持久化；</li>
 * <li>把名单广播给所有客户端（含新加入的玩家）；</li>
 * <li>名单启用时，由 {@code RoleAssignmentPool} 在建池时读取本配置，仅接管职业的启用/禁用（不接管数量、无概率）。</li>
 * </ul>
 * 数据库按玩家 UUID 分键存储，这里使用一个固定的“配置 UUID”表示服务器全局配置。
 */
public final class RoleRosterManager {
    /** 在 MySQL 玩家数据表中代表“服务器全局配置”的固定 UUID。 */
    public static final UUID CONFIG_UUID = new UUID(0L, 0x5_2_0_5_7_E_4_1L);
    public static final String PART = "role_roster";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_TIMEOUT_MS = 4_000L;
    private static final Path LOCAL_FILE = FabricLoader.getInstance().getConfigDir().resolve("sre_role_roster.json");

    private static volatile RoleRosterState state = RoleRosterState.createDefault();
    private static volatile MinecraftServer server;

    private RoleRosterManager() {
    }

    public static void registerEvents() {
        ServerLifecycleEvents.SERVER_STARTED.register(RoleRosterManager::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> flushBlocking());
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            if (SREConfig.instance().enableRoster)
                sendTo(handler.getPlayer());
        });
    }

    public static RoleRosterState getState() {
        if (!SREConfig.instance().enableRoster)
            return RoleRosterState.DISABLE;
        return state;
    }

    public static boolean isRoleEnabled(SRERole role) {
        if (!SREConfig.instance().enableRoster || !state.enabled)
            return true;
        return state.roleCounts.getOrDefault(role.identifier().toString(), 1) > 0;
    }

    public static boolean isModifierEnabled(SREModifier modifier) {
        if (!SREConfig.instance().enableRoster || !state.enabled)
            return true;
        return state.modifierCounts.getOrDefault(modifier.identifier().toString(), 1) > 0;
    }

    public static boolean isEnabled() {
        return SREConfig.instance().enableRoster && state.enabled;
    }

    // ------------------------------------------------------------------
    // 生命周期 / 加载
    // ------------------------------------------------------------------

    private static void onServerStarted(MinecraftServer startedServer) {
        if (!SREConfig.instance().enableRoster)
            return;
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
        if (!SREConfig.instance().enableRoster) {
            return;
        }
        RoleRosterState incoming = fromJson(json);
        boolean enabled = incoming.enabled;
        incoming.normalized();
        state.roleCounts = incoming.roleCounts;
        state.modifierCounts = incoming.modifierCounts;
        state.enabled = enabled;
        afterMutated();
    }

    public static void setEnabled(boolean enabled) {

        if (!SREConfig.instance().enableRoster) {
            return;
        }
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

    private static void afterMutated() {
        state.version = Math.max(System.currentTimeMillis(), state.version + 1L);
        state.normalized();
        writeLocalFile();
        saveToDatabase();
        broadcast();
    }

    // ------------------------------------------------------------------
    // 职业分配接入：名单的启用/禁用由 RoleAssignmentPool 在建池时直接读取
    // （见 RoleAssignmentPool#createInternal），名单只决定职业是否参与，不接管数量，也没有概率。
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 网络同步
    // ------------------------------------------------------------------

    private static void broadcast() {
        if (!SREConfig.instance().enableRoster)
            return;
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
