package org.agmas.noellesroles.katana;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import io.wifi.starrailexpress.event.OnGameEnd;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 武士刀的每名玩家服务端状态（连招进度、格挡内置冷却、衔接窗口）。
 *
 * <p>与 {@code DreamHealthComponent.VIRTUAL_HEALTH_DEATH_MARKS} 相同的组织方式：
 * 静态 Map + 开局 / 结局事件统一清空，无需 CCA 组件。
 */
public final class KatanaState {

    private KatanaState() {
    }

    // ── 招式编号 ──
    public static final int MOVE_SWEEP = 1;   // 第一招式：横扫
    public static final int MOVE_THRUST = 2;  // 第二招式：突刺
    public static final int MOVE_SLASH = 3;   // 第三招式：劈砍

    // ── 格挡参数 ──
    /** 格挡前摇：0.4 秒（衔接招式后为 0）。 */
    public static final int BLOCK_WINDUP_TICKS = 8;
    /** 格挡有效时间：1.2 秒（前摇之后）。 */
    public static final int BLOCK_ACTIVE_TICKS = 24;
    /** 使用时长合计（原版 use duration）：前摇 + 有效时间。 */
    public static final int BLOCK_USE_DURATION = BLOCK_WINDUP_TICKS + BLOCK_ACTIVE_TICKS;
    /** 未衔接招式的格挡结束后进入的内置冷却：5 秒。 */
    public static final int BLOCK_INTERNAL_COOLDOWN_TICKS = 100;
    /** 招式成功命中后，衔接格挡（0 前摇、无内置冷却）的有效窗口：3 秒。 */
    public static final int LINKED_WINDOW_TICKS = 60;
    /** 击杀玩家后武士刀进入的物品冷却：10 秒。 */
    public static final int KILL_COOLDOWN_TICKS = 200;

    // ── 动画同步用的实体事件字节（客户端在 handleEntityEvent 中消费） ──
    /** 本次攻击使用的招式（100 + 招式编号）。 */
    public static final byte EVENT_MOVE_USED_BASE = 100;
    /** 下一招式预测（110 + 招式编号），供客户端无延迟预测动画。 */
    public static final byte EVENT_NEXT_MOVE_BASE = 110;
    /** 格挡内置冷却开始（客户端记录 5 秒冷却）。 */
    public static final byte EVENT_BLOCK_COOLDOWN_START = 120;
    /** 格挡内置冷却清除（命中后刷新）。 */
    public static final byte EVENT_BLOCK_COOLDOWN_CLEAR = 121;

    /** 单名玩家的武士刀状态。 */
    public static class PlayerState {
        /** 下一次攻击使用的招式（1~3），命中后才推进。 */
        public int nextMove = MOVE_SWEEP;
        /** 格挡内置冷却截止时间（gameTime），早于该时间无法开始格挡。 */
        public long blockCooldownUntil;
        /** 衔接窗口截止：最后一次招式命中后的 {@link #LINKED_WINDOW_TICKS} 内格挡视为衔接。 */
        public long linkedUntil;
        /** 本次格挡开始时是否处于衔接窗口内（决定前摇与结束后是否进入内置冷却）。 */
        public boolean blockLinked;
    }

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    static {
        // 开局重置所有玩家的连招状态
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> STATES.clear());
        // 游戏结束时清空，避免残留到下一局
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> STATES.clear());
    }

    /** 获取（必要时创建）玩家的武士刀状态。 */
    public static PlayerState get(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
    }

    /** 广播实体事件（服务端专用）。 */
    public static void broadcast(ServerPlayer player, byte eventId) {
        player.level().broadcastEntityEvent(player, eventId);
    }
}
