package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.command_block.MustardRole;
import org.agmas.noellesroles.game.command_block.MustardRules;

/** 芥末的一次替伤窗口与核心购买次数。 */
public class MustardRoleData extends SimpleRoleData {
    public long guardUntil;
    public int corePurchases;
    /** 虚拟武器常附带一次原版受击反馈，替伤后不能让反馈再命中本体。 */
    public long redirectedVirtualAt = Long.MIN_VALUE;

    public ServerPlayer redirectedVirtualTarget;
    public ServerPlayer redirectedVirtualAttacker;
    public MustardRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean hasGuard(long now) {
        return MustardRules.guardActive(guardUntil, now);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer viewer) {
        return viewer == player;
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer self) {
            MustardRole.endPuppets(self);
            MustardRole.clearCorePrice(self);
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putLong("GuardUntil", guardUntil);
        tag.putInt("CorePurchases", corePurchases);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        guardUntil = tag.getLong("GuardUntil");
        corePurchases = tag.getInt("CorePurchases");
    }
}
