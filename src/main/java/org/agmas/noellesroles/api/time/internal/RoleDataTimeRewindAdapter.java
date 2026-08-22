/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.api.time.internal;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.api.time.TimeRewindComponentAdapter;

/** Keeps the active RoleData instance coherent with the rewound role CCA. */
public final class RoleDataTimeRewindAdapter
        implements TimeRewindComponentAdapter<SRERoleDataPlayerComponent> {
    private static final String ROLE_ID = "role_id";
    private static final String DATA_CLASS = "data_class";
    private static final String DATA = "data";

    @Override
    public void writeSnapshot(SRERoleDataPlayerComponent component, CompoundTag tag,
            HolderLookup.Provider registryLookup) {
        if (component.playerRole != null) {
            tag.putString(ROLE_ID, component.playerRole.identifier().toString());
        }
        if (component.roleData != null) {
            tag.putString(DATA_CLASS, component.roleData.getClass().getName());
            CompoundTag data = new CompoundTag();
            component.roleData.writeToRewindNbt(data, registryLookup);
            tag.put(DATA, data);
        }
    }

    @Override
    public void readSnapshot(SRERoleDataPlayerComponent component, CompoundTag tag,
            HolderLookup.Provider registryLookup) {
        SRERole currentRole = SREGameWorldComponent.KEY.get(component.getPlayer().level())
                .getRole(component.getPlayer());
        String capturedRoleId = tag.getString(ROLE_ID);
        String currentRoleId = currentRole == null ? "" : currentRole.identifier().toString();
        if (!capturedRoleId.equals(currentRoleId)) {
            throw new IllegalStateException("role CCA restored to " + currentRoleId
                    + " but RoleData snapshot belongs to " + capturedRoleId);
        }

        if (!tag.contains(DATA)) {
            if (currentRole == null) {
                component.clear();
            } else if (component.playerRole == null
                    || !component.playerRole.identifier().equals(currentRole.identifier())
                    || component.roleData != null) {
                if (component.roleData != null) {
                    component.roleData.clear();
                }
                component.serverInit();
                if (component.roleData != null) {
                    throw new IllegalStateException("captured role had no RoleData but recreated "
                            + component.roleData.getClass().getName());
                }
            }
            return;
        }

        String capturedClass = tag.getString(DATA_CLASS);
        RoleData roleData = component.roleData;
        boolean instanceMatches = component.playerRole != null
                && component.playerRole.identifier().toString().equals(capturedRoleId)
                && roleData != null
                && roleData.getClass().getName().equals(capturedClass);
        if (!instanceMatches) {
            if (roleData != null) {
                roleData.clear();
            }
            component.serverInit();
            roleData = component.roleData;
        }
        if (roleData == null || !roleData.getClass().getName().equals(capturedClass)) {
            throw new IllegalStateException("cannot recreate RoleData " + capturedClass);
        }
        roleData.readFromRewindNbt(tag.getCompound(DATA), registryLookup);
    }

    @Override
    public int restorePriority() {
        // RoleData is constructed from the restored role/game components.
        return 1_000;
    }
}
