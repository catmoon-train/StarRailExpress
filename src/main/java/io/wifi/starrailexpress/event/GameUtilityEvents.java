package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import io.wifi.starrailexpress.util.TrueFalseResult;

/**
 * 游戏工具的精确扩展时点，替代对自有 GameUtils 的 HEAD/TAIL 注入。
 * 监听器只在模组初始化时注册，不按玩家或对局动态注册。
 */
public final class GameUtilityEvents {
    private GameUtilityEvents() {
    }

    /**
     * 五参数 killPlayer 入口，在线程转交和强杀升级之前触发，包含强制死亡。
     * 按注册顺序执行，首个 false 立即取消；这不是死亡成功通知。
     * 与旧 HEAD 注入一样在调用线程执行，不要假定此时已经转交服务端线程。
     */
    public static final Event<BeforeKill> BEFORE_KILL = EventFactory.createArrayBacked(BeforeKill.class,
            GameUtilityEvents::beforeKillInvoker);

    static BeforeKill beforeKillInvoker(BeforeKill[] listeners) {
        return (victim, spawnBody, killer, reason, force) -> {
            for (BeforeKill listener : listeners) {
                if (!listener.allow(victim, spawnBody, killer, reason, force)) return false;
            }
            return true;
        };
    }

    /** 复活前否决，位于状态清理与传送之前；回房间复活的 destination 为 null。 */
    public static final Event<BeforeRevive> ALLOW_REVIVE = EventFactory.createArrayBacked(BeforeRevive.class,
            GameUtilityEvents::beforeReviveInvoker);

    static BeforeRevive beforeReviveInvoker(BeforeRevive[] listeners) {
        return (player, destination) -> {
            for (BeforeRevive listener : listeners) if (!listener.allow(player, destination)) return false;
            return true;
        };
    }

    @FunctionalInterface
    public interface BeforeRevive {
        boolean allow(ServerPlayer player, @Nullable net.minecraft.world.phys.Vec3 destination);
    }

    /** 完整 resetPlayer 的末尾；不挪用位于基础重置中段的 ResetPlayerEvent。 */
    public static final Event<AfterReset> AFTER_RESET = EventFactory.createArrayBacked(AfterReset.class,
            listeners -> player -> {
                for (AfterReset listener : listeners) listener.afterReset(player);
            });

    /** initializeGame 入口，仍早于延迟准备与最终准入闸门，可能被多次调用。 */
    public static final Event<BeforeInitialize> BEFORE_INITIALIZE = EventFactory.createArrayBacked(
            BeforeInitialize.class, listeners -> level -> {
                for (BeforeInitialize listener : listeners) listener.beforeInitialize(level);
            });

    /** 普通存活查询入口，首个非 PASS 结果生效，默认继续基础判定。 */
    public static final Event<AliveOverride> ALIVE_OVERRIDE = EventFactory.createArrayBacked(AliveOverride.class,
            listeners -> player -> {
                for (AliveOverride listener : listeners) {
                    TrueFalseResult result = listener.query(player);
                    if (result != null && !result.isPass()) return result;
                }
                return TrueFalseResult.PASS;
            });

    @FunctionalInterface
    public interface AliveOverride {
        TrueFalseResult query(Player player);
    }

    @FunctionalInterface
    public interface BeforeKill {
        boolean allow(Player victim, boolean spawnBody, @Nullable Player killer,
                ResourceLocation reason, boolean force);
    }

    @FunctionalInterface
    public interface AfterReset {
        void afterReset(ServerPlayer player);
    }

    @FunctionalInterface
    public interface BeforeInitialize {
        void beforeInitialize(ServerLevel level);
    }
}
