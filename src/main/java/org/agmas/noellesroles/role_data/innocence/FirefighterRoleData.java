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

/**
 * 消防员职业数据。
 * <p>
 * 与独裁者的「独裁之书」一致：用职业数据记录本局购买状态，
 * 每局重新赋予职业时数据会重建，因此自然实现「一局只能购买一次」。
 */
public class FirefighterRoleData extends SimpleRoleData {

    /** 消防斧本局是否已经购买过。 */
    public boolean axePurchased;

    /** 灭火器本局是否已经购买过。 */
    public boolean extinguisherPurchased;

    public FirefighterRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 购买标记是纯服务端逻辑，无需同步给客户端
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
