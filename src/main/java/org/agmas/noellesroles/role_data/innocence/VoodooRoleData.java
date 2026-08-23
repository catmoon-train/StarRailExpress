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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class VoodooRoleData extends SimpleRoleData {

    public UUID target;

    @Override
    public void init() {
        this.target = player.getUUID();
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public VoodooRoleData(RoleDataContext context) {
        super(context);
    }

    public void setTarget(UUID target) {
        this.target = target;
        this.sync();
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putUUID("target", player.getUUID());
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.target = tag.contains("target") ? tag.getUUID("target") : player.getUUID();
    }
}
