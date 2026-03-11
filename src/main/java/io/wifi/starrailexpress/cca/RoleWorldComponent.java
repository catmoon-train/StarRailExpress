package io.wifi.starrailexpress.cca;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class RoleWorldComponent implements AutoSyncedComponent {
    public static final ComponentKey<RoleWorldComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("roles"),
            RoleWorldComponent.class);
    private final Level world;
    HashMap<String, Role> pathToRole = new HashMap<>();

    private final HashMap<UUID, Role> roles = new HashMap<>();

    public RoleWorldComponent(Level world) {
        this.world = world;
    }

    public void addRole(Player player, Role role) {
        if (player == null) {
            return;
        }
        this.addRole(player.getUUID(), role);
    }

    public void addRole(UUID player, Role role, boolean sync) {
        if (player == null) {
            return;
        }
        this.roles.put(player, role);
        if (sync)
            this.sync();
    }

    public void addRole(UUID player, Role role) {
        this.addRole(player, role, true);
    }

    public void resetRole(Role role) {
        this.resetRole(role, true);
    }

    public void resetRole(Role role, boolean sync) {
        roles.entrySet().removeIf(entry -> entry.getValue() == role);
        if (sync)
            this.sync();
    }

    public void sync() {
        KEY.sync(this.world);
    }

    public void setRoles(List<UUID> players, Role role) {
        if (players == null) {
            return;
        }
        resetRole(role);

        for (UUID player : players) {
            if (player == null)
                continue;
            addRole(player, role);
        }
        this.sync();
    }

    public HashMap<UUID, Role> getRoles() {
        return roles;
    }

    public Role getRole(Player player) {
        if (player == null) {
            return null;
        }
        return getRole(player.getUUID());
    }

    public @Nullable Role getRole(UUID uuid) {
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

    public List<UUID> getAllWithRole(Role role) {
        List<UUID> ret = new ArrayList<>();
        roles.forEach((uuid, playerRole) -> {
            if (playerRole == role) {
                ret.add(uuid);
            }
        });

        return ret;
    }

    public boolean isRole(@NotNull Player player, Role role) {
        if (player == null) {
            return role == null;
        }
        return isRole(player.getUUID(), role);
    }

    public boolean isRole(@NotNull UUID uuid, Role role) {
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
        this.roles.clear();
        if (sync)
            this.sync();
    }

    public void clearRoleMap() {
        this.roles.clear();
        this.sync();
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

    public @Nullable Role getRoleFromPath(String path) {
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
        return getRole(player) != null && getRole(player).canSeeTeammateKiller();
    }

    public boolean isKillerTeamRole(Role role) {
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

    public static boolean isKillerTeamRoleStatic(Role role) {
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

                    Role role = getRoleFromPath(rolePath);
                    if (role != null) {
                        this.roles.putIfAbsent(playerUid, role);
                    }
                }
            }
        }
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag nbtCompound, HolderLookup.Provider wrapperLookup) {
        if (this.roles.isEmpty())
            return;
        var roleInfoCompund = new CompoundTag();
        for (Entry<UUID, Role> info : roles.entrySet()) {
            UUID pUuid = info.getKey();
            if (pUuid == null)
                continue;
            String keyName = pUuid.toString();
            Role role = info.getValue();
            if (role == null)
                continue;
            String roleId = role.identifier().getPath();
            roleInfoCompund.putString(keyName, roleId);
        }
        nbtCompound.put("roles", roleInfoCompund);
    }
}