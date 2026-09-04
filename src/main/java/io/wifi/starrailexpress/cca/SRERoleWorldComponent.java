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

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.fabricmc.api.EnvType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.util.CheckEnvironment;

import java.util.*;
import java.util.Map.Entry;

public class SRERoleWorldComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<SRERoleWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("roles"),
            SRERoleWorldComponent.class);

    // 同步包类型：FULL=全量快照（新玩家加入/重生/换维度由CCA触发），DIFF=差异（仅变更过的玩家条目）
    private static final byte MODE_FULL = 0;
    private static final byte MODE_DIFF = 1;

    private final Level world;
    HashMap<String, SRERole> pathToRole = new HashMap<>();

    private final HashMap<UUID, SRERole> roles = new HashMap<>();

    // 自上次发包以来角色真正发生变化的玩家，差异包只包含这些玩家
    private final Set<UUID> dirtyUuids = new HashSet<>();
    // 本tick内收到过sync()请求，推迟到serverTick统一发包，把多次广播合并成一次
    private boolean syncPending = false;
    // 仅服务端在差异发包期间为true；区分"我们自己的差异同步"与"CCA的新玩家全量同步"
    private boolean diffMode = false;

    public SRERoleWorldComponent(Level world) {
        this.world = world;
    }

    @Override
    public void serverTick() {
        flushPendingDiff();
    }

    /**
     * 把本tick内积累的角色差异统一广播给世界内所有玩家。没有任何改动时不发包。
     */
    private void flushPendingDiff() {
        if (this.world.isClientSide)
            return;
        synchronized (this.roles) {
            if (!this.syncPending || this.dirtyUuids.isEmpty()) {
                this.syncPending = false;
                return;
            }
        }
        this.diffMode = true;
        try {
            KEY.sync(this.world);
        } finally {
            this.diffMode = false;
            synchronized (this.roles) {
                this.dirtyUuids.clear();
                this.syncPending = false;
            }
        }
    }

    public void addRole(Player player, SRERole role) {
        if (player == null) {
            return;
        }
        this.addRole(player.getUUID(), role);
    }

    public void addRole(UUID player, SRERole role, boolean sync) {
        if (player == null) {
            return;
        }
        synchronized (this.roles) {
            if (this.roles.put(player, role) != role) {
                this.dirtyUuids.add(player);
            }
        }
        if (sync)
            this.sync();
    }

    public void addRole(UUID player, SRERole role) {
        this.addRole(player, role, true);
    }

    public void removeRole(Player player) {
        this.removeRole(player.getUUID());
    }

    public void removeRole(UUID player) {
        this.removeRole(player, true);
    }

    public void removeRole(UUID player, boolean sync) {
        if (player == null)
            return;
        synchronized (this.roles) {
            if (this.roles.remove(player) != null) {
                this.dirtyUuids.add(player);
            }
        }
        if (sync)
            this.sync();
    }

    public void resetRole(SRERole role) {
        this.resetRole(role, true);
    }

    public void resetRole(SRERole role, boolean sync) {
        synchronized (this.roles) {
            this.roles.entrySet().removeIf(entry -> {
                if (entry.getValue() == role) {
                    this.dirtyUuids.add(entry.getKey());
                    return true;
                }
                return false;
            });
        }
        if (sync)
            this.sync();
    }

    public void sync() {
        if (this.world.isClientSide)
            return;
        synchronized (this.roles) {
            if (this.dirtyUuids.isEmpty())
                return;
            this.syncPending = true;
        }
    }

    /**
     * 强制把当前整张角色表全量重发给世界内所有玩家。仅供管理命令/手动校准使用
     * （sync()在有dirty时走差异，全量重发才能兜底补发）。
     */
    public void forceSyncAll() {
        if (this.world.isClientSide)
            return;
        try {
            KEY.sync(this.world);
        } finally {
            synchronized (this.roles) {
                this.dirtyUuids.clear();
                this.syncPending = false;
            }
        }
    }

    public void setRoles(List<UUID> players, SRERole role) {
        if (players == null) {
            return;
        }
        resetRole(role, false);

        for (UUID player : players) {
            if (player == null)
                continue;
            addRole(player, role, false);
        }
        this.sync();
    }

    public HashMap<UUID, SRERole> getRoles() {
        return roles;
    }

    public SRERole getRole(Player player) {
        if (player == null) {
            return null;
        }
        return getRole(player.getUUID());
    }

    public @Nullable SRERole getRole(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return roles.get(uuid);
    }

    public List<UUID> getAllKillerTeamPlayers() {
        List<UUID> ret = new ArrayList<>();
        roles.forEach((uuid, playerRole) -> {
            if ((isKillerTeamRole(playerRole))) {
                ret.add(uuid);
            }
        });

        return ret;
    }

    public List<UUID> getAllKillerPlayers() {
        List<UUID> ret = new ArrayList<>();
        roles.forEach((uuid, playerRole) -> {
            if ((playerRole.canUseKiller() && !playerRole.isNeutrals())) {
                ret.add(uuid);
            }
        });

        return ret;
    }

    public List<UUID> getAllWithRole(SRERole role) {
        List<UUID> ret = new ArrayList<>();
        roles.forEach((uuid, playerRole) -> {
            if (playerRole == role) {
                ret.add(uuid);
            }
        });

        return ret;
    }

    public boolean isRole(@NotNull Player player, SRERole role) {
        if (player == null) {
            return role == null;
        }
        return isRole(player.getUUID(), role);
    }

    public boolean isRole(@NotNull UUID uuid, SRERole role) {
        if (uuid == null) {
            return role == null;
        }
        return this.roles.get(uuid) == role;
    }

    public boolean isNeutralForKiller(@NotNull Player player) {
        return getRole(player) != null && getRole(player).isNeutralForKiller();
    }

    public boolean canUseKillerFeatures(@NotNull Player player) {
        return getRole(player) != null && getRole(player).canUseKiller();
    }

    public boolean isInnocent(@NotNull Player player) {
        return getRole(player) != null && getRole(player).isInnocent();
    }

    public void clearRoleMap(boolean sync) {
        synchronized (this.roles) {
            this.dirtyUuids.addAll(this.roles.keySet());
            this.roles.clear();
        }
        if (sync)
            this.sync();
    }

    public void clearRoleMap() {
        this.clearRoleMap(true);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        return true;
    }

    public void reloadPathToRole() {
        pathToRole.clear();
        for (var r : TMMRoles.ROLES.entrySet()) {
            var role = r.getValue();
            pathToRole.putIfAbsent(role.identifier().getPath(), role);
        }
    }

    public @Nullable SRERole getRoleFromPath(String path) {
        if (pathToRole.containsKey(path)) {
            return pathToRole.get(path);
        } else {
            reloadPathToRole();
            if (pathToRole.containsKey(path)) {
                return pathToRole.get(path);
            }
        }
        return null;
    }

    public boolean canSeeKillerTeammate(Player player) {
        return getRole(player) != null && getRole(player).canSeeTeammateKillerRole();
    }

    public boolean isKillerTeamRole(SRERole role) {
        if (role == null)
            return false;
        if (role.canUseKiller())
            return true;
        if (role.isNeutralForKiller())
            return true;
        return false;
    }

    public boolean isKillerTeam(Player player) {
        if (player != null) {
            var role = this.getRole(player);
            if (role == null)
                return false;
            if (role.canUseKiller())
                return true;
            if (role.isNeutralForKiller())
                return true;
        }
        return false;
    }

    public static boolean isKillerTeamRoleStatic(SRERole role) {
        if (role == null)
            return false;
        if (role.canUseKiller())
            return true;
        if (role.isNeutralForKiller())
            return true;
        return false;
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        // this.lockedToSupporters = nbtCompound.getBoolean("LockedToSupporters");
        // this.enableWeights = nbtCompound.getBoolean("EnableWeights");
        synchronized (this.roles) {
            this.roles.clear();

            if (nbtCompound.contains("roles", CompoundTag.TAG_COMPOUND)) {
                var roleInfoCompund = nbtCompound.getCompound("roles");
                Set<String> keys = roleInfoCompund.getAllKeys();
                for (var p_name : keys) {
                    if (roleInfoCompund.contains(p_name, CompoundTag.TAG_STRING)) {
                        String rolePath = roleInfoCompund.getString(p_name);
                        UUID playerUid = null;
                        try {
                            playerUid = UUID.fromString(p_name);
                        } catch (Exception e) {

                        }

                        if (playerUid == null)
                            continue;

                        SRERole role = getRoleFromPath(rolePath);
                        if (role != null) {
                            this.roles.putIfAbsent(playerUid, role);
                        }
                    }
                }
            }
            // 读档只填充服务端状态，不广播；新玩家加入时CCA会发送全量快照
            this.dirtyUuids.clear();
            this.syncPending = false;
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        if (this.roles.isEmpty())
            return;
        var roleInfoCompund = new CompoundTag();
        for (Entry<UUID, SRERole> info : roles.entrySet()) {
            UUID pUuid = info.getKey();
            if (pUuid == null)
                continue;
            String keyName = pUuid.toString();
            SRERole role = info.getValue();
            if (role == null)
                continue;
            String roleId = role.identifier().getPath();
            roleInfoCompund.putString(keyName, roleId);
        }
        nbtCompound.put("roles", roleInfoCompund);
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        synchronized (this.roles) {
            if (this.diffMode) {
                // 差异：只写自上次发包以来角色变更过的玩家条目（空路径=该玩家已无角色，通知删除）
                buf.writeByte(MODE_DIFF);
                buf.writeVarInt(this.dirtyUuids.size());
                for (UUID uuid : this.dirtyUuids) {
                    SRERole role = this.roles.get(uuid);
                    writeEntry(buf, uuid, role == null ? null : role.identifier().getPath());
                }
            } else {
                // 全量快照：新玩家加入/重生/换维度时由CCA触发，等价于旧版的整表覆盖
                buf.writeByte(MODE_FULL);
                int count = 0;
                for (SRERole role : this.roles.values()) {
                    if (role != null)
                        count++;
                }
                buf.writeVarInt(count);
                for (Map.Entry<UUID, SRERole> entry : this.roles.entrySet()) {
                    if (entry.getValue() != null)
                        writeEntry(buf, entry.getKey(), entry.getValue().identifier().getPath());
                }
            }
        }
    }

    @Override
    @CheckEnvironment(EnvType.CLIENT)
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        final byte mode = buf.readByte();
        final int entryCount = buf.readVarInt();
        if (mode == MODE_FULL) {
            this.roles.clear();
        }
        for (int i = 0; i < entryCount; i++) {
            UUID uuid = buf.readUUID();
            String rolePath = buf.readUtf();
            if (rolePath.isEmpty()) {
                // 空路径 = 该玩家已没有角色，删除键保持与全量覆盖一致
                this.roles.remove(uuid);
                continue;
            }
            SRERole role = this.getRoleFromPath(rolePath);
            if (role != null) {
                this.roles.put(uuid, role);
            }
        }
    }

    private static void writeEntry(RegistryFriendlyByteBuf buf, UUID uuid, @Nullable String rolePath) {
        buf.writeUUID(uuid);
        buf.writeUtf(rolePath == null ? "" : rolePath);
    }

    public void syncWith(ServerPlayer player) {
        KEY.syncWith(player, this.world.asComponentProvider());
    }
}
