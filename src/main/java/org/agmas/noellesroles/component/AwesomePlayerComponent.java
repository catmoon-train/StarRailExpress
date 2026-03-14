package org.agmas.noellesroles.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnPlayerDeath;
import io.wifi.starrailexpress.game.GameUtils;

import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 记者最近死亡记录组件
 */
public class AwesomePlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<AwesomePlayerComponent> KEY = ModComponents.AWESOME;

    // 持有该组件的玩家
    private final Player player;

    // 该玩家上次附近玩家死亡效果
    public int nearByDeathTime = 0;
    public static final int nearByDeathTimeRecordTime = 30 * 20;// 30s

    public int nearByDeathTime() {
        return nearByDeathTime;
    }

    /**
     * 构造函数
     */
    public AwesomePlayerComponent(Player player) {
        this.player = player;
    }

    public static void registerEvents() {
        OnPlayerDeath.EVENT.register((victim, resourceLocation) -> {
            var players = victim.level().players();
            for (var player : players) {
                if (GameUtils.isPlayerAliveAndSurvival(player)) {
                    if (player.distanceToSqr(victim) <= 25) {
                        AwesomePlayerComponent component = AwesomePlayerComponent.KEY.maybeGet(player).orElse(null);
                        if (component != null) {
                            component.setNearByDeathTime(nearByDeathTimeRecordTime); // 30s
                            // componentKey.sync();
                        }
                    }
                }

            }
        });
    }

    @Override
    public void init() {
        this.nearByDeathTime = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 设置时间
     * 
     * @param ticks 时间（tick），20 tick = 1 秒
     */
    public void setNearByDeathTime(int ticks) {
        this.nearByDeathTime = ticks;
        this.sync();
    }

    public static SREGameWorldComponent gameWorldComponent = null;

    @Override
    public boolean shouldSyncWith(ServerPlayer sp) {
        if (gameWorldComponent == null) {
            gameWorldComponent = SREGameWorldComponent.KEY.get(sp.level());
        }
        if (gameWorldComponent.isRole(sp, ModRoles.AWESOME_BINGLUS)) {
            return true;
        }
        return false;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.AWESOME.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 服务端每 tick 减少冷却时间
        if (this.nearByDeathTime > 0) {
            this.nearByDeathTime--;
            // 每5秒同步一次（而不是每 tick），减少网络压力
            if (this.nearByDeathTime % 100 == 0 || this.nearByDeathTime == 0) {
                this.sync();
            }
        }
    }

    @Override
    public void clientTick() {
        // 客户端也进行冷却计算（用于预测显示）
        if (this.nearByDeathTime > 0) {
            this.nearByDeathTime--;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("nearbydeathtime", this.nearByDeathTime);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.nearByDeathTime = tag.contains("nearbydeathtime") ? tag.getInt("nearbydeathtime") : 0;
    }
}