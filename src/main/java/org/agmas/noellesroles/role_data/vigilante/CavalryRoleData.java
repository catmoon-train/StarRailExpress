package org.agmas.noellesroles.role_data.vigilante;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/**
 * 骑兵（Cavalry）的职业数据。
 * <p>
 * 只记录一件事：商店里的「突进 III」附魔是否已经买过（一局仅生效一次）。
 */
public class CavalryRoleData extends SimpleRoleData {

    /** 是否已经为下界合金矛附魔过突进（一局一次）。 */
    public boolean lungeBought = false;

    public CavalryRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putBoolean("LungeBought", this.lungeBought);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.lungeBought = tag.getBoolean("LungeBought");
    }
}
