package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameConstants;
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
 * 骷髅是「骨头拼起来的」，很脆：下落超过 {@link #FALL_DEATH_HEIGHT} 格直接摔死。
 * 判定方式参考地图配置 {@code fallToDeathHeight}（下落距离达到阈值即按
 * {@code fall_damage} 死因判死），区别是这里不依赖地图配置，对骷髅恒为 6 格。
 */
public class SkeletonRoleData extends SimpleRoleData {

    /** 骷髅的摔落致死高度（格）。 */
    public static final float FALL_DEATH_HEIGHT = 6.0F;

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
        checkFallDeath();
        // 始终伪装成原版骷髅：掉线重连 / 开局重置会丢掉伪装状态，这里每 tick 兜底补上
        if (player instanceof ServerPlayer serverPlayer && GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            SkeletonRole.applySkeletonDisguise(serverPlayer);
        }
    }

    /** 下落超过 6 格直接摔死（不需要等到落地才结算）。 */
    private void checkFallDeath() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
            return;
        }
        if (serverPlayer.isCreative() || serverPlayer.isSpectator()) {
            return;
        }
        if (serverPlayer.fallDistance > FALL_DEATH_HEIGHT) {
            GameUtils.killPlayer(serverPlayer, true, null, GameConstants.DeathReasons.FALL_DAMAGE);
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
