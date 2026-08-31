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

package io.wifi.starrailexpress.roster;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;

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
        return state.roleCounts.getOrDefault(role.identifier().toString(), 0) > 0;
    }

    public static boolean isModifierEnabled(SREModifier modifier) {
        if (!SREConfig.instance().enableRoster || !state.enabled)
            return true;
        return state.modifierCounts.getOrDefault(modifier.identifier().toString(), 0) > 0;
    }

    public static boolean isEnabled() {
        return SREConfig.instance().enableRoster && state.enabled;
    }

    // ------------------------------------------------------------------
    // 生命周期 / 加载
    // ------------------------------------------------------------------

    private static void onServerStarted(MinecraftServer startedServer) {
        server = startedServer;
        loadDataFromFile();
        loadDataFromServer();
    }

    public static boolean loadDataFromFile() {

        if (!SREConfig.instance().enableRoster)
            return false;
        // 先读本地文件（即使数据库不可用也有配置可用）
        RoleRosterState local = readLocalFile();
        if (local != null) {
            state = local.normalized();
        } else {
            return false;
        }
        broadcast();
        return true;
    }

    public static boolean loadDataFromServer() {

        if (!SREConfig.instance().enableRoster)
            return false;
        // 再尝试从数据库覆盖（数据库版本更新时为准）
        if (!isDatabaseEnabled()) {
            return false;
        }
        if (SREConfig.instance().ignoreMysqlRosterConfig) {
            return false;
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
        return true;
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
        if (!SREConfig.instance().ignoreMysqlRosterConfig) {
            saveToDatabase();
        }
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
        if (!SREConfig.instance().enableRoster) {
            return;
        }
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

        if (SREConfig.instance().ignoreMysqlRosterConfig) {
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

        if (SREConfig.instance().ignoreMysqlRosterConfig) {
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

    public static void randomRoster(int roleNum, int modifierNum) {
        randomRoster(roleNum, modifierNum, false);
    }

    public static void randomRoster(int roleNum, int modifierNum, boolean forceCount) {
        // state.version++;
        state.roleCounts.clear();
        state.modifierCounts.clear();

        // 1. 计算各阵营目标数量
        RoleCounts targets = computeTargetCounts(roleNum);
        if (targets.isInvalid() || modifierNum <= 0) {
            return;
        }

        // 2. 分类所有角色和修饰符
        RolePools pools = classifyAllRoles();
        List<SREModifier> allModifiers = new ArrayList<>(HMLModifiers.MODIFIERS);

        // 3. 随机初选
        Set<SRERole> selectedRoles = initialRoleSelection(pools, targets);
        Set<SREModifier> selectedModifiers = initialModifierSelection(allModifiers, modifierNum);

        // 4. 补充关联（职业/修饰符）
        expandRelations(selectedRoles, selectedModifiers);

        // 5. 删除多余项（保证数量不超目标）
        if (forceCount) {
            trimModifiers(selectedModifiers, modifierNum);
            trimRoles(selectedRoles, targets, pools);
        }

        // 6. 应用结果并广播
        applyResult(selectedRoles, selectedModifiers);
    }

    // ---------- 辅助数据结构 ----------
    private static class RoleCounts {
        final int killer;
        final int neutrals;
        final int vigilante;
        final int innocent;

        RoleCounts(int killer, int neutrals, int vigilante, int innocent) {
            this.killer = killer;
            this.neutrals = neutrals;
            this.vigilante = vigilante;
            this.innocent = innocent;
        }

        boolean isInvalid() {
            return killer < 0 || neutrals < 0 || vigilante < 0 || innocent < 0;
        }
    }

    public static class RolePools {
        final List<SRERole> innocent;
        final List<SRERole> neutrals;
        final List<SRERole> vigilante;
        final List<SRERole> killer;

        RolePools(List<SRERole> innocent, List<SRERole> neutrals,
                List<SRERole> vigilante, List<SRERole> killer) {
            this.innocent = innocent;
            this.neutrals = neutrals;
            this.vigilante = vigilante;
            this.killer = killer;
        }

        public boolean isEmpty() {
            return innocent.isEmpty() || neutrals.isEmpty() ||
                    vigilante.isEmpty() || killer.isEmpty();
        }
    }

    // ---------- 步骤方法 ----------
    private static RoleCounts computeTargetCounts(int roleNum) {
        int killer = roleNum / 6;
        int neutrals = roleNum / 6;
        int vigilante = roleNum / 6;
        int innocent = roleNum - killer - neutrals - vigilante;
        return new RoleCounts(killer, neutrals, vigilante, innocent);
    }

    private static RolePools classifyAllRoles() {
        List<SRERole> innocent = new ArrayList<>();
        List<SRERole> neutrals = new ArrayList<>();
        List<SRERole> vigilante = new ArrayList<>();
        List<SRERole> killer = new ArrayList<>();

        for (SRERole role : TMMRoles.ROLES.values()) {
            if (role.canUseKiller() && !role.isNeutrals() && !role.isInnocent()) {
                killer.add(role);
            } else if (role.isNeutrals()) {
                neutrals.add(role);
            } else if (role.isVigilanteTeam()) {
                vigilante.add(role);
            } else {
                innocent.add(role);
            }
        }
        return new RolePools(innocent, neutrals, vigilante, killer);
    }

    private static Set<SRERole> initialRoleSelection(RolePools pools, RoleCounts targets) {
        Collections.shuffle(pools.innocent);
        Collections.shuffle(pools.vigilante);
        Collections.shuffle(pools.neutrals);
        Collections.shuffle(pools.killer);

        Set<SRERole> selected = new HashSet<>();
        selected.addAll(pools.innocent.subList(0, Math.min(pools.innocent.size(), targets.innocent)));
        selected.addAll(pools.killer.subList(0, Math.min(pools.killer.size(), targets.killer)));
        selected.addAll(pools.neutrals.subList(0, Math.min(pools.neutrals.size(), targets.neutrals)));
        selected.addAll(pools.vigilante.subList(0, Math.min(pools.vigilante.size(), targets.vigilante)));
        return selected;
    }

    private static Set<SREModifier> initialModifierSelection(List<SREModifier> allModifiers, int modifierNum) {
        Collections.shuffle(allModifiers);
        return new HashSet<>(allModifiers.subList(0, Math.min(allModifiers.size(), modifierNum)));
    }

    private static void expandRelations(Set<SRERole> selectedRoles, Set<SREModifier> selectedModifiers) {
        // 由角色扩展
        for (SRERole role : new ArrayList<>(selectedRoles)) {
            selectedRoles.addAll(role.relatedRoles);
            selectedRoles.addAll(role.occupationRoles);
            selectedRoles.addAll(role.occupationedRoles);
            selectedModifiers.addAll(role.relatedModifiers);
        }
        // 由修饰符扩展
        for (SREModifier modifier : new ArrayList<>(selectedModifiers)) {
            selectedRoles.addAll(modifier.relatedRoles);
        }
    }

    private static void trimModifiers(Set<SREModifier> selectedModifiers, int targetCount) {
        // 找出可删除的修饰符（无关联角色）
        Set<SREModifier> removable = new HashSet<>();
        for (SREModifier mod : selectedModifiers) {
            if (mod.relatedRoles.isEmpty()) {
                removable.add(mod);
            }
        }
        List<SREModifier> removableList = new ArrayList<>(removable);
        Collections.shuffle(removableList);

        while (selectedModifiers.size() > targetCount && !removableList.isEmpty()) {
            SREModifier toRemove = removableList.remove(0);
            selectedModifiers.remove(toRemove);
        }
    }

    private static void trimRoles(Set<SRERole> selectedRoles, RoleCounts targets, RolePools pools) {
        // 收集各阵营当前数量及可删除角色
        int nowKiller = 0, nowNeutrals = 0, nowVigilante = 0, nowInnocent = 0;
        Set<SRERole> removableKiller = new HashSet<>();
        Set<SRERole> removableNeutrals = new HashSet<>();
        Set<SRERole> removableVigilante = new HashSet<>();
        Set<SRERole> removableInnocent = new HashSet<>();

        for (SRERole role : selectedRoles) {
            // 统计当前数量
            if (role.canUseKiller() && !role.isNeutrals() && !role.isInnocent()) {
                nowKiller++;
            } else if (role.isNeutrals()) {
                nowNeutrals++;
            } else if (role.isVigilanteTeam()) {
                nowVigilante++;
            } else {
                nowInnocent++;
            }

            // 判断是否可删除（无任何关联）
            if (role.occupationRoles.isEmpty() && role.occupationedRoles.isEmpty() && role.relatedRoles.isEmpty()
                    && role.relatedModifiers.isEmpty()) {
                if (role.canUseKiller() && !role.isNeutrals() && !role.isInnocent()) {
                    removableKiller.add(role);
                } else if (role.isNeutrals()) {
                    removableNeutrals.add(role);
                } else if (role.isVigilanteTeam()) {
                    removableVigilante.add(role);
                } else {
                    removableInnocent.add(role);
                }
            }
        }

        // 分别按阵营删除多余（打乱后逐个移除）
        trimRoleGroup(selectedRoles, removableKiller, nowKiller, targets.killer);
        trimRoleGroup(selectedRoles, removableNeutrals, nowNeutrals, targets.neutrals);
        trimRoleGroup(selectedRoles, removableVigilante, nowVigilante, targets.vigilante);
        trimRoleGroup(selectedRoles, removableInnocent, nowInnocent, targets.innocent);
    }

    private static void trimRoleGroup(Set<SRERole> selected, Set<SRERole> removable,
            int currentCount, int targetCount) {
        List<SRERole> list = new ArrayList<>(removable);
        Collections.shuffle(list);
        while (currentCount > targetCount && !list.isEmpty()) {
            SRERole toRemove = list.remove(0);
            selected.remove(toRemove);
            currentCount--;
        }
    }

    private static void applyResult(Set<SRERole> selectedRoles, Set<SREModifier> selectedModifiers) {
        for (SRERole role : selectedRoles) {
            state.roleCounts.put(role.identifier().toString(), 1);
        }
        for (SREModifier modifier : selectedModifiers) {
            state.modifierCounts.put(modifier.identifier().toString(), 1);
        }
        broadcast();
    }
}
