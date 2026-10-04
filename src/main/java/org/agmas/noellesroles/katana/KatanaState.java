package org.agmas.noellesroles.katana;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import io.wifi.starrailexpress.event.OnGameEnd;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    // ── 突刺（第二招式）位移结算参数 ──
    /** 突刺位移的最长时长（tick）：0.5 秒，与无碰撞效果时长一致。 */
    public static final int THRUST_DASH_MAX_TICKS = 10;
    /** 突刺连续未命中多少次后连招回到第一招式。 */
    public static final int THRUST_MAX_MISSES = 3;
    /** 突刺途中碰撞判定的包围盒膨胀（格）。 */
    public static final double THRUST_HIT_MARGIN = 0.75D;
    /** 突刺碰撞造成的虚拟伤害。 */
    public static final int THRUST_VIRTUAL_DAMAGE = 7;

    // ── 动画同步用的实体事件字节（客户端在 handleEntityEvent 中消费） ──
    /** 本次攻击使用的招式（100 + 招式编号）。 */
    public static final byte EVENT_MOVE_USED_BASE = 100;
    /** 下一招式预测（110 + 招式编号），供客户端无延迟预测动画。 */
    public static final byte EVENT_NEXT_MOVE_BASE = 110;
    /** 格挡内置冷却开始（客户端记录 5 秒冷却）。 */
    public static final byte EVENT_BLOCK_COOLDOWN_START = 120;
    /** 格挡内置冷却清除（命中后刷新）。 */
    public static final byte EVENT_BLOCK_COOLDOWN_CLEAR = 121;
    /** 本次格挡为「衔接格挡」（无前摇 → 抬臂瞬间到位）。 */
    public static final byte EVENT_BLOCK_START_LINKED = 122;
    /** 本次格挡为「普通格挡」（有 0.4 秒前摇 → 抬臂随前摇渐进）。 */
    public static final byte EVENT_BLOCK_START_NORMAL = 123;

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

        // ── 突刺（第二招式）位移状态（仅服务端使用） ──
        /** 是否正在突刺位移中。 */
        public boolean thrustDashing = false;
        /** 突刺方向。 */
        public Vec3 thrustDirection = Vec3.ZERO;
        /** 上一 tick 所在位置。 */
        public Vec3 thrustLastPos = Vec3.ZERO;
        /** 本次突刺是否已经发生过实际位移。 */
        public boolean thrustHasMoved = false;
        /** 突刺剩余结算时长（tick）。 */
        public int thrustTicksLeft = 0;
        /** 本次突刺碰撞到的玩家数。 */
        public int thrustHitCount = 0;
        /** 突刺连续未命中的次数（达到 {@link #THRUST_MAX_MISSES} 后连招回到第一招式）。 */
        public int thrustMissCount = 0;
        /** 本次突刺已碰撞的玩家，防止同一目标重复受伤。 */
        public final Set<UUID> thrustHitPlayers = new HashSet<>();

        /** 结束并清空突刺状态（不动连招进度与未命中计数，推进与否由调用方决定）。 */
        public void stopThrust() {
            this.thrustDashing = false;
            this.thrustTicksLeft = 0;
            this.thrustDirection = Vec3.ZERO;
            this.thrustLastPos = Vec3.ZERO;
            this.thrustHasMoved = false;
            this.thrustHitCount = 0;
            this.thrustHitPlayers.clear();
        }
    }

    private static final Map<UUID, PlayerState> STATES = new ConcurrentHashMap<>();

    /**
     * 每名玩家的武士刀<b>虚拟血量伤害倍率</b>；缺省或 1 = 不修改。
     *
     * <p>供职业效果挂载，例如剑客「淬血」期间 ×2。与 {@link #STATES} 一样是
     * 静态 Map + 开局 / 结局事件统一清空。
     */
    private static final Map<UUID, Integer> VIRTUAL_DAMAGE_MULTIPLIERS = new ConcurrentHashMap<>();

    static {
        // 开局重置所有玩家的连招状态
        GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
            STATES.clear();
            VIRTUAL_DAMAGE_MULTIPLIERS.clear();
        });
        // 游戏结束时清空，避免残留到下一局
        OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
            STATES.clear();
            VIRTUAL_DAMAGE_MULTIPLIERS.clear();
        });
    }

    /**
     * 设置该玩家武士刀的虚拟血量伤害倍率。
     *
     * @param multiplier 倍率；{@code <= 1} 视为不修改并清除记录
     */
    public static void setVirtualDamageMultiplier(Player player, int multiplier) {
        if (player == null) {
            return;
        }
        if (multiplier <= 1) {
            VIRTUAL_DAMAGE_MULTIPLIERS.remove(player.getUUID());
        } else {
            VIRTUAL_DAMAGE_MULTIPLIERS.put(player.getUUID(), multiplier);
        }
    }

    /** 清除该玩家的虚拟血量伤害倍率（退出职业 / 局末时调用）。 */
    public static void clearVirtualDamageMultiplier(Player player) {
        if (player != null) {
            VIRTUAL_DAMAGE_MULTIPLIERS.remove(player.getUUID());
        }
    }

    /** 该玩家武士刀的虚拟血量伤害倍率，缺省为 1。 */
    public static int virtualDamageMultiplier(Player player) {
        if (player == null) {
            return 1;
        }
        return VIRTUAL_DAMAGE_MULTIPLIERS.getOrDefault(player.getUUID(), 1);
    }

    /** 按该玩家的倍率缩放武士刀的虚拟血量伤害。 */
    public static int scaleVirtualDamage(Player attacker, int baseDamage) {
        return baseDamage * virtualDamageMultiplier(attacker);
    }

    /** 获取（必要时创建）玩家的武士刀状态。 */
    public static PlayerState get(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), uuid -> new PlayerState());
    }

    /**
     * 格挡抬臂进度（0~1）：<b>普通格挡</b>在 0.4 秒前摇期间线性抬臂。
     *
     * <p><b>衔接格挡</b>（招式命中后的 0 前摇格挡，见
     * {@link PlayerState#blockLinked}）没有前摇时间可言，由调用方直接给 1.0，
     * 表现为「瞬间架刀」。
     *
     * @param ticksUsing    已使用物品的 tick 数
     * @param partialTicks 本 tick 的插值余量（客户端用）
     */
    public static float blockRaiseProgress(int ticksUsing, float partialTicks) {
        float progress = (ticksUsing + partialTicks) / BLOCK_WINDUP_TICKS;
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    /** 广播实体事件（服务端专用）。 */
    public static void broadcast(ServerPlayer player, byte eventId) {
        player.level().broadcastEntityEvent(player, eventId);
    }
}
