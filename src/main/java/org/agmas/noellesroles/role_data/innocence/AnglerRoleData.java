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
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerCatchHandler;
import org.jetbrains.annotations.NotNull;

public class AnglerRoleData extends SimpleRoleData {

    /** 下一次普通钓获额外再 roll 一次稀有表。 */
    public boolean bonusRareNext = false;

    public AnglerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        if (player instanceof ServerPlayer serverPlayer) {
            AnglerCatchHandler.giveStartingRod(serverPlayer);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (bonusRareNext) {
            tag.putBoolean("bonus_rare_next", true);
        }
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        bonusRareNext = tag.getBoolean("bonus_rare_next");
    }
}
