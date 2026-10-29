package org.agmas.noellesroles.role_data.killer;

import java.util.UUID;
import org.agmas.noellesroles.game.command_block.Error404Role;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 404 本人的状态随职业实例创建和销毁，不使用全局玩家计时表。 */
public class Error404RoleData extends SimpleRoleData {
    public boolean high;
    public long windupAt;
    public long readyAt;
    public UUID aimTarget;
    public long aimStarted;
    public UUID selected;
    public Vec3 anchor;
    public long nextActionAt;

    public Error404RoleData(RoleDataContext context) { super(context); }

    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) { return recipient == player; }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer self) Error404Role.tickHunter(self, this);
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer self) Error404Role.leaveHigh(self, this);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registry) { tag.putBoolean("high", high); }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registry) { high = tag.getBoolean("high"); }
}
