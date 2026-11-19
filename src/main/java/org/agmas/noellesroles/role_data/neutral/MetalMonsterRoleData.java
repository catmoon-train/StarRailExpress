package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 金属怪的每人状态：钢卷形态的速度、朝向、反弹锁定与二技能致命窗口。
 *
 * <p>冷却由统一技能系统管理，这里只存「本次技能期间」的即时状态；
 * 变回人形时 {@link #reset()} 会一次性清空。
 */
public class MetalMonsterRoleData extends SimpleRoleData {

    /** 是否正在钢卷形态。 */
    public boolean rolling;
    /** 当前水平速度（格 / tick），范围 [0, MAX_SPEED]；0 表示静止，需按前进键起步。 */
    public double speed;
    /** 当前竖直速度（格 / tick，<= 0）：钢卷自己维护的重力积分结果，落地后归零。 */
    public double fallSpeed;
    /** 当前前进方向的单位向量。 */
    public double dirX;
    public double dirZ;
    /** 反弹锁定期：锁定期内不能前进 / 转向，结束后恢复操作。 */
    public int bounceTicks;
    /** 客户端上报的输入位与朝向，以及最后一次收到的游戏时间。 */
    public int inputBits;
    public float inputYaw;
    public long inputGameTime = Long.MIN_VALUE;
    /** 二技能致命窗口结束时刻（游戏时间）。 */
    public long lethalUntil;
    /** 每个目标最近一次被撞击的 tick，避免同一 tick 重复结算。 */
    public final Map<UUID, Long> hitTicks = new HashMap<>();

    public MetalMonsterRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    /** 清除钢卷形态的所有状态。 */
    public void reset() {
        rolling = false;
        speed = 0.0D;
        fallSpeed = 0.0D;
        dirX = 0.0D;
        dirZ = 0.0D;
        bounceTicks = 0;
        inputBits = 0;
        inputGameTime = Long.MIN_VALUE;
        lethalUntil = 0L;
        hitTicks.clear();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("rolling", rolling);
        tag.putDouble("speed", speed);
        tag.putDouble("dirX", dirX);
        tag.putDouble("dirZ", dirZ);
        tag.putInt("bounce", bounceTicks);
        tag.putLong("lethalUntil", lethalUntil);
        tag.putLong("inputTime", inputGameTime);
        tag.putInt("inputBits", inputBits);
        tag.putFloat("inputYaw", inputYaw);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        rolling = tag.getBoolean("rolling");
        speed = tag.getDouble("speed");
        dirX = tag.getDouble("dirX");
        dirZ = tag.getDouble("dirZ");
        bounceTicks = tag.getInt("bounce");
        lethalUntil = tag.getLong("lethalUntil");
        inputGameTime = tag.getLong("inputTime");
        inputBits = tag.getInt("inputBits");
        inputYaw = tag.getFloat("inputYaw");
    }
}
