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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class SkincrawlerRoleData extends SimpleRoleData {


    public static final int STEAL_COOLDOWN = 60 * 20;
    public static final int MAX_BLOCK_CHARGES = 1;

    public int stealCooldown;
    public UUID stolenSkin;
    public int blockCharges = MAX_BLOCK_CHARGES;

    public SkincrawlerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        stealCooldown = 0;
        stolenSkin = null;
        blockCharges = MAX_BLOCK_CHARGES;
        sync();
    }

    @Override
    public void clear() {
        init();
    }

    public boolean isActive() {
        if (player == null || player.level().isClientSide()) return false;
        return SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.SKINCRAWLER);
    }

    @Override
    public void serverTick() {
        if (!isActive()) return;
        if (stealCooldown > 0) { stealCooldown--; sync(); }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("stealCooldown", stealCooldown);
        tag.putInt("blockCharges", blockCharges);
        if (stolenSkin != null) tag.putString("stolenSkin", stolenSkin.toString());
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider lookup) {
        stealCooldown = tag.getInt("stealCooldown");
        blockCharges = tag.contains("blockCharges") ? tag.getInt("blockCharges") : MAX_BLOCK_CHARGES;
        stolenSkin = tag.contains("stolenSkin") ? UUID.fromString(tag.getString("stolenSkin")) : null;
    }

}
