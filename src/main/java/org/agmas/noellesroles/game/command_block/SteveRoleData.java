package org.agmas.noellesroles.game.command_block;

import java.util.HashMap;

import org.agmas.noellesroles.packet.ScanAllTaskPointsPayload;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** Steve 每人独立的采矿结算间隔，不跨职业或跨局保留。 */
public class SteveRoleData extends SimpleRoleData {
    private long nextMineAt;

    public SteveRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void init() {
        if (player instanceof ServerPlayer server && GameUtils.taskBlocks != null) {
            ServerPlayNetworking.send(server, new ScanAllTaskPointsPayload(new HashMap<>(GameUtils.taskBlocks)));
        }
    }

    /** 同一工具的一轮开采只能结算一次，重复完成包不会多发材料或扣耐久。 */
    public boolean tryHarvest(long now, int miningTicks) {
        if (now < nextMineAt) {
            return false;
        }
        nextMineAt = now + miningTicks;
        return true;
    }

    // 结算间隔只由服务端持有，客户端不需要也不能决定奖励时刻。
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
