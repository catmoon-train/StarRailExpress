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

/** Steve 独立的虚拟材料与采矿结算间隔，不跨职业或跨局保留。 */
public class SteveRoleData extends SimpleRoleData {
    private long nextMineAt;
    private final int[] materials = new int[SteveMaterial.values().length];

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

    public int count(SteveMaterial material) {
        return materials[material.ordinal()];
    }

    /** 获得材料只增加虚拟余额，不生成可拾取或可掉落的物品。 */
    public void add(SteveMaterial material, int amount) {
        if (amount <= 0) return;
        int index = material.ordinal();
        materials[index] = (int) Math.min(Integer.MAX_VALUE, (long) materials[index] + amount);
        sync();
    }

    /** 余额不足或数量非法时不扣费。 */
    public boolean take(SteveMaterial material, int amount) {
        if (amount <= 0 || count(material) < amount) return false;
        materials[material.ordinal()] -= amount;
        sync();
        return true;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer viewer) {
        return viewer == player;
    }

    // 只同步虚拟余额；结算间隔由服务端持有，客户端不能决定奖励时刻。
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        for (SteveMaterial material : SteveMaterial.values()) {
            tag.putInt(material.key(), count(material));
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        for (SteveMaterial material : SteveMaterial.values()) {
            materials[material.ordinal()] = Math.max(0, tag.getInt(material.key()));
        }
    }
}
