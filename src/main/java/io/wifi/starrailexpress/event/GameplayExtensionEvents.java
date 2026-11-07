package io.wifi.starrailexpress.event;

import com.mojang.brigadier.context.CommandContext;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.game.utils.RoleInstance;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 通用玩法扩展点，不依赖具体职业、修饰符或活动实现。
 * allow 事件默认放行、首个 false 短路；override 事件首个非 null 短路（null 表示执行原逻辑）；
 * result/reward 事件按注册顺序传递结果；通知事件按注册顺序全部调用。仅在模块初始化时注册监听器。
 */
public final class GameplayExtensionEvents {
    private GameplayExtensionEvents() {}

    @FunctionalInterface public interface KillGate {
        boolean allow(Player victim, boolean body, @Nullable Player killer, ResourceLocation reason, boolean forced);
    }
    @FunctionalInterface public interface KillCoins {
        boolean allow(Player victim, Player killer, ResourceLocation reason);
    }
    @FunctionalInterface public interface PlayerGate { boolean allow(Player player); }
    @FunctionalInterface public interface PlayerIntOverride { @Nullable Integer get(Player player); }
    @FunctionalInterface public interface PlayerNotice { void accept(Player player); }
    @FunctionalInterface public interface KillTime { boolean handle(SREGameTimeComponent time); }
    @FunctionalInterface public interface EndResult {
        WinStatus apply(ServerLevel level, WinStatus proposed, WinStatus result);
    }
    @FunctionalInterface public interface RolePoolResult { List<RoleInstance> apply(List<RoleInstance> result); }
    @FunctionalInterface public interface WelcomePayload {
        AnnounceWelcomePayload apply(ServerLevel level, SREGameWorldComponent game, List<ServerPlayer> players,
                AnnounceWelcomePayload payload);
    }
    @FunctionalInterface public interface KillerCount { @Nullable Integer get(int playerCount); }
    @FunctionalInterface public interface PlaneIntro { boolean allow(ServerLevel level, Entity plane); }
    @FunctionalInterface public interface QuestReward {
        int apply(Player player, String quest, int streak, boolean parallel, int reward);
    }
    @FunctionalInterface public interface QuestFinished {
        void accept(Player player, String quest, int streak, boolean parallel);
    }
    @FunctionalInterface public interface TaskTimer { void accept(SREPlayerTaskComponent component); }
    @FunctionalInterface public interface MinigameFinished {
        void accept(ServerPlayer player, BlockPos pos, int reward, String minigameId);
    }
    @FunctionalInterface public interface ArmorConsume { @Nullable Boolean consume(Player player, int amount); }
    @FunctionalInterface public interface ArmorSync { boolean allow(Player player, ServerPlayer target); }
    @FunctionalInterface public interface MountPlayer {
        Player apply(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit);
    }
    @FunctionalInterface public interface PlatterUse {
        @Nullable InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit);
    }
    @FunctionalInterface public interface DoorCollision {
        @Nullable VoxelShape get(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context);
    }
    @FunctionalInterface public interface NoteTick { void accept(NoteEntity note); }
    @FunctionalInterface public interface CrowbarUse { @Nullable InteractionResult use(UseOnContext context); }
    @FunctionalInterface public interface Infection { boolean allow(Player player, Player infector); }
    @FunctionalInterface public interface StuckCommand {
        @Nullable Integer handle(CommandContext<CommandSourceStack> context);
    }

    /** killPlayer 的 HEAD；强制死亡同样受此否决。 */
    public static final Event<KillGate> ALLOW_KILL_ENTRY = killGate();
    /** 已取得真实凶手事件 invoker，但尚未调用；否决仅跳过解析，不清空调用方凶手。 */
    public static final Event<KillGate> ALLOW_KILLER_RESOLUTION = killGate();
    /** 真实凶手及已有入口保护处理之后，OnKillPlayerTriggered 字段访问之前。 */
    public static final Event<KillGate> ALLOW_KILL_AFTER_ATTRIBUTION = killGate();
    /** 弱盾组件非 null 后、读取层数之前；非 null 结果代替 getter，避免多余求值。 */
    public static final Event<PlayerIntOverride> WEAK_ARMOR_OVERRIDE = playerIntOverride();
    /** shouldGiveKillerBalance 调用之前；否决不会调用原方法或其内部事件。 */
    public static final Event<KillCoins> ALLOW_BASE_KILL_COINS = killCoins();
    /** grantKillCoin 调用之前，与基础金币分别查询，保留中间余额变化的影响。 */
    public static final Event<KillCoins> ALLOW_EXTRA_KILL_COINS = killCoins();
    /** addKillRewardTime 的 HEAD；首个 true 表示已处理，完全跳过默认时间逻辑。 */
    public static final Event<KillTime> HANDLE_KILL_REWARD_TIME = createArrayBacked(KillTime.class, listeners -> time -> {
        for (var listener : listeners) if (listener.handle(time)) return true;
        return false;
    });
    /** allowGameEnd 的全部 RETURN，晚于原有 START/内置/END 事件与结算优先级。 */
    public static final Event<EndResult> GAME_END_RESULT = createArrayBacked(EndResult.class,
            listeners -> (level, proposed, result) -> {
                for (var listener : listeners) result = listener.apply(level, proposed, result);
                return result;
            });
    /** 最完整 getAllRoles 重载的全部 RETURN，包括空列表提前返回。 */
    public static final Event<RolePoolResult> ROLE_POOL_RESULT = createArrayBacked(RolePoolResult.class, listeners -> result -> {
        for (var listener : listeners) result = listener.apply(result);
        return result;
    });
    /** 欢迎包已构造、尚未 send；每位玩家单独查询，目标人数不由 core 改写。 */
    public static final Event<WelcomePayload> WELCOME_PAYLOAD = createArrayBacked(WelcomePayload.class,
            listeners -> (level, game, players, payload) -> {
                for (var listener : listeners) payload = listener.apply(level, game, players, payload);
                return payload;
            });
    public static final Event<KillerCount> KILLER_COUNT_OVERRIDE = createArrayBacked(KillerCount.class, listeners -> count -> {
        for (var listener : listeners) {
            Integer result = listener.get(count);
            if (result != null) return result;
        }
        return null;
    });
    /** 默认开场入口 HEAD，早于模式过滤。 */
    public static final Event<PlayerGate> ALLOW_DEFAULT_INTRO = playerGate();
    /** 飞机开场入口 HEAD，早于飞机存活检查。 */
    public static final Event<PlaneIntro> ALLOW_PLANE_INTRO = createArrayBacked(PlaneIntro.class, listeners -> (level, plane) -> {
        for (var listener : listeners) if (!listener.allow(level, plane)) return false;
        return true;
    });
    /** 默认任务奖励计算之后、账本登记之前；不触及固定奖励分支。 */
    public static final Event<QuestReward> QUEST_REWARD = createArrayBacked(QuestReward.class,
            listeners -> (player, quest, streak, parallel, reward) -> {
                for (var listener : listeners) reward = listener.apply(player, quest, streak, parallel, reward);
                return reward;
            });
    /** 原任务处理 TAIL；无职业时也触发，在调用方收入抑制作用域内执行。 */
    public static final Event<QuestFinished> AFTER_QUEST = createArrayBacked(QuestFinished.class,
            listeners -> (player, quest, streak, parallel) -> {
                for (var listener : listeners) listener.accept(player, quest, streak, parallel);
            });
    /** 双端技能组件 tick 的 HEAD；否决跳过整个 tick，而不只冷却。 */
    public static final Event<PlayerGate> ALLOW_ABILITY_TICK = playerGate();
    /** 首次 nextTaskTimer GETFIELD 后、原自减写回前。通知不能替代原自减的已读取值。 */
    public static final Event<TaskTimer> BEFORE_TASK_TIMER_DECREMENT = createArrayBacked(TaskTimer.class,
            listeners -> component -> { for (var listener : listeners) listener.accept(component); });
    /** 小游戏任务成功 RETURN 前且 sync 后；失败返回不触发。 */
    public static final Event<MinigameFinished> MINIGAME_COMPLETED = createArrayBacked(MinigameFinished.class,
            listeners -> (player, pos, reward, id) -> {
                for (var listener : listeners) listener.accept(player, pos, reward, id);
            });
    /** 两个 stopPsycho 重载各自的 TAIL；无参转调时触发两次。 */
    public static final Event<PlayerNotice> PSYCHO_STOPPED = createArrayBacked(PlayerNotice.class,
            listeners -> player -> { for (var listener : listeners) listener.accept(player); });
    public static final Event<PlayerIntOverride> ARMOR_COUNT_OVERRIDE = playerIntOverride();
    public static final Event<ArmorConsume> ARMOR_CONSUME_OVERRIDE = createArrayBacked(ArmorConsume.class,
            listeners -> (player, amount) -> {
                for (var listener : listeners) {
                    Boolean result = listener.consume(player, amount);
                    if (result != null) return result;
                }
                return null;
            });
    /** 首个 true 允许额外同步；默认 false 时继续原同步过滤，不构成否决。 */
    public static final Event<ArmorSync> ALLOW_ARMOR_SYNC = createArrayBacked(ArmorSync.class, listeners -> (player, target) -> {
        for (var listener : listeners) if (listener.allow(player, target)) return true;
        return false;
    });
    /** 坐具与马桶 useWithoutItem 的 HEAD，先于所有交互分支；父类转调时再次触发。 */
    public static final Event<MountPlayer> MOUNT_PLAYER = createArrayBacked(MountPlayer.class,
            listeners -> (state, level, pos, player, hit) -> {
                for (var listener : listeners) player = listener.apply(state, level, pos, player, hit);
                return player;
            });
    public static final Event<PlatterUse> PLATTER_USE = createArrayBacked(PlatterUse.class,
            listeners -> (state, level, pos, player, hit) -> {
                for (var listener : listeners) {
                    var result = listener.use(state, level, pos, player, hit);
                    if (result != null) return result;
                }
                return null;
            });
    public static final Event<DoorCollision> DOOR_COLLISION = createArrayBacked(DoorCollision.class,
            listeners -> (state, level, pos, context) -> {
                for (var listener : listeners) {
                    var result = listener.get(state, level, pos, context);
                    if (result != null) return result;
                }
                return null;
            });
    /** 普通便签 tick 的 TAIL，双端均通知。 */
    public static final Event<NoteTick> AFTER_NOTE_TICK = createArrayBacked(NoteTick.class,
            listeners -> note -> { for (var listener : listeners) listener.accept(note); });
    public static final Event<CrowbarUse> CROWBAR_USE = createArrayBacked(CrowbarUse.class, listeners -> context -> {
        for (var listener : listeners) {
            var result = listener.use(context);
            if (result != null) return result;
        }
        return null;
    });
    public static final Event<Infection> ALLOW_INFECTION = createArrayBacked(Infection.class, listeners -> (player, infector) -> {
        for (var listener : listeners) if (!listener.allow(player, infector)) return false;
        return true;
    });
    /** /stuck 的 HEAD，在原异常处理外；非 null 结果立即返回。 */
    public static final Event<StuckCommand> STUCK_COMMAND = createArrayBacked(StuckCommand.class, listeners -> context -> {
        for (var listener : listeners) {
            Integer result = listener.handle(context);
            if (result != null) return result;
        }
        return null;
    });

    private static Event<KillGate> killGate() {
        return createArrayBacked(KillGate.class, listeners -> (victim, body, killer, reason, forced) -> {
            for (var listener : listeners) if (!listener.allow(victim, body, killer, reason, forced)) return false;
            return true;
        });
    }
    private static Event<KillCoins> killCoins() {
        return createArrayBacked(KillCoins.class, listeners -> (victim, killer, reason) -> {
            for (var listener : listeners) if (!listener.allow(victim, killer, reason)) return false;
            return true;
        });
    }
    private static Event<PlayerGate> playerGate() {
        return createArrayBacked(PlayerGate.class, listeners -> player -> {
            for (var listener : listeners) if (!listener.allow(player)) return false;
            return true;
        });
    }
    private static Event<PlayerIntOverride> playerIntOverride() {
        return createArrayBacked(PlayerIntOverride.class, listeners -> player -> {
            for (var listener : listeners) {
                Integer result = listener.get(player);
                if (result != null) return result;
            }
            return null;
        });
    }
}
