package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * 独裁者职业数据。
 */
public class DictatorRoleData extends SimpleRoleData {

    /** 裁决之剑右键尸体时锁定的尸体（选择界面提交时用它校验答案） */
    public UUID pendingBodyUuid;

    /** 独裁之书本局是否已经购买过（仅能购买一次） */
    public boolean bookPurchased;

    public DictatorRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 无需同步给客户端：尸体锁定与购买标记都是纯服务端逻辑
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
