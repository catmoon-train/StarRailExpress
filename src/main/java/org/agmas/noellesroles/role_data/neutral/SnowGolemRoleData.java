package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.content.entity.SnowballRollEntity;
import org.agmas.noellesroles.game.roles.neutral.snow_golem.SnowGolemRole;
import org.agmas.noellesroles.init.ModEntities;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 雪傀儡的职业数据。
 *
 * <p>
 * 只承担「每人状态」：在雪地上累计的移动距离、上一次的坐标、融化计时、
 * 以及「谁点燃了我」的归属记录。所有规则本体都在 {@link SnowGolemRole} 里。
 *
 * <p>
 * 这些都是纯服务端数据（客户端不需要知道推动进度），所以同步 NBT 留空；
 * 雪球的大小 / 滚动角由雪球实体自己同步。
 */
public class SnowGolemRoleData extends SimpleRoleData {

    /** 融化计时（每 {@link SnowGolemRole#MELT_INTERVAL} tick 结算一次）。 */
    public int meltTicks;
    /** 在雪地上累计走过的距离（满 4 格推 / 撑大一次雪球）。 */
    public double snowDistance;
    /** 上一 tick 的水平坐标；{@link Double#MIN_VALUE} 表示还没初始化。 */
    public double lastX = Double.MIN_VALUE;
    public double lastZ = Double.MIN_VALUE;

    /** 最近一次被施加火焰效果的玩家（融化致死的击杀者）。 */
    @Nullable
    public UUID igniter;
    /** 记录 {@link #igniter} 时的游戏时间，用于丢弃陈旧的归属。 */
    public long igniterGameTime;

    public SnowGolemRoleData(RoleDataContext context) {
        super(context);
    }

    /** 取该玩家的雪傀儡数据（不是雪傀儡 / 没有数据时返回 null）。 */
    @Nullable
    public static SnowGolemRoleData get(ServerPlayer player) {
        return RoleData.getNullable(SnowGolemRoleData.class, player);
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        // 掉线重连 / 开局重置会丢伪装状态，这里每 tick 兜底补上（同骷髅）
        SnowGolemRole.applySnowGolemDisguise(serverPlayer);
        // 保暖值必须恒为满值：这里每 tick 重新登记一次锁定，兜底掉线重连 / 状态重建
        SnowGolemRole.keepWarmthFull(serverPlayer);
        SnowGolemRole.tickMelt(serverPlayer);
        SnowGolemRole.tickSnowballPush(serverPlayer);
        // 兜底：万一「取暖」任务还是被别的路径塞进来了（回溯 / 场景任务自愈），直接拿掉
        SnowGolemRole.dropWarmthTask(serverPlayer);
    }

    /**
     * 解析「谁点燃了我」：只认仍然存活、且不是自己的玩家；太久以前的记录直接丢弃。
     *
     * @return 点燃该雪傀儡的玩家，没有则返回 {@code null}
     */
    @Nullable
    public ServerPlayer resolveIgniter(ServerPlayer self) {
        if (igniter == null) {
            return null;
        }
        ServerPlayer attacker = self.serverLevel().getServer().getPlayerList().getPlayer(igniter);
        if (attacker == null || attacker == self || !GameUtils.isPlayerAliveAndSurvival(attacker)) {
            return null;
        }
        return attacker;
    }

    /**
     * 取当前正在被这颗雪傀儡推动的雪球。
     *
     * <p>
     * 数据里不缓存实体引用（实体可能被清场 / 换维度），每次按 UUID 在维度里查，
     * 顺便把已经被移除的雪球自然过滤掉。
     *
     * @return 正在推动的雪球；没有则返回 {@code null}
     */
    @Nullable
    public SnowballRollEntity heldBall(ServerLevel level) {
        UUID self = player.getUUID();
        for (SnowballRollEntity ball : level.getEntities(ModEntities.SNOWBALL_ROLL, entity -> true)) {
            if (ball.isRemoved() || !ball.isHeld()) {
                continue;
            }
            if (self.equals(ball.getOwnerId())) {
                return ball;
            }
        }
        return null;
    }

    // ==================== 同步 ====================

    /**
     * 纯服务端数据，不需要同步给客户端（雪球的尺寸由实体自己的 SynchedEntityData 同步）。
     */
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
