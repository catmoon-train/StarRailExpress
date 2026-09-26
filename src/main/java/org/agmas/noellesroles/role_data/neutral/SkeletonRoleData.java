package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.neutral.skeleton.SkeletonRole;

import java.util.UUID;

/**
 * 骷髅职业数据。
 *
 * <p>
 * 骷髅是「骨头拼起来的」，很脆：下落超过 {@link SkeletonRole#FALL_DEATH_HEIGHT} 格，落地即按
 * {@code fall_damage} 死因摔死（判定见 {@link SkeletonRole#onFallOnGround}，不依赖地图配置，
 * 对骷髅恒为 6 格）。本类只承担数据侧职责：伪装补挂与状态同步。
 */
public class SkeletonRoleData extends SimpleRoleData {

    /**
     * 召唤者：使用「骸骨之书」把该玩家复活成骷髅的人。
     * 结算时骷髅跟随召唤者获胜（见 {@code SkeletonRole#didPlayerWin}）。
     */
    public UUID summoner;

    public SkeletonRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public void serverTick() {
        // 始终伪装成原版骷髅：掉线重连 / 开局重置会丢掉伪装状态，这里每 tick 兜底补上
        if (player instanceof ServerPlayer serverPlayer && GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            SkeletonRole.applySkeletonDisguise(serverPlayer);
        }
    }

    // ==================== 同步 ====================

    /**
     * 骷髅没有需要同步给客户端的状态（摔死判定、伪装补挂都是纯服务端逻辑），
     * 所以这里留空即可；{@code shouldSyncWith} 用接口的默认实现（只同步给本人）。
     */
    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }
}
