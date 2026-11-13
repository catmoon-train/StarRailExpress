package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;

/** 每局律令使用记录，由职业数据生命周期清理，界面只读取同步副本。 */
public final class DecreeMageRoleData extends SimpleRoleData {
    private int usedMask;

    public DecreeMageRoleData(RoleDataContext context) { super(context); }
    public boolean isUsed(Decree decree) { return (usedMask & (1 << decree.ordinal())) != 0; }
    public void markUsed(Decree decree) {
        usedMask |= 1 << decree.ordinal();
        sync();
    }
    @Override public boolean shouldSyncWith(ServerPlayer observer) { return observer == player; }
    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("decreeUsed", usedMask);
    }
    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        usedMask = tag.getInt("decreeUsed");
    }
}
