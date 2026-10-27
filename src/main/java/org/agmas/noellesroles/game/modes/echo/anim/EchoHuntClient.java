package org.agmas.noellesroles.game.modes.echo.anim;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端的动作时间轴：只保存「谁在什么时候开始播哪个动作」，供渲染层按系统时间插值。
 *
 * <p>所有时间都是本地 {@link System#nanoTime()}，绝不参与判定；动作时长是固定的表现常量，
 * 与服务器 tick 无关，因此暂停/卡顿时动作只是看起来慢一点，不会影响任何结算。
 *
 * <p>去重：每个玩家记最近一次的包序号，序号不增的包直接丢弃（网络重发、乱序）。
 */
public final class EchoHuntClient {
    private EchoHuntClient() { }

    /** 一次动作的本地时间窗。 */
    public static final class Action {
        public final EchoHuntPayload.Kind kind;
        public final EchoSkillCastAnim.Style style;
        public final boolean empowered;
        public final long startedAt;
        public final long duration;

        Action(EchoHuntPayload.Kind kind, EchoSkillCastAnim.Style style, boolean empowered, long startedAt, long duration) {
            this.kind = kind; this.style = style; this.empowered = empowered;
            this.startedAt = startedAt; this.duration = duration;
        }

        /** 0~1 的进度，已做 clamp；不属于该动作时返回 -1。 */
        public float progress(long now) {
            if (now < startedAt) return 0F;
            long elapsed = now - startedAt;
            if (elapsed >= duration) return -1F;
            return elapsed / (float) duration;
        }

        public boolean alive(long now) { return now - startedAt < duration; }
    }

    private static final Map<UUID, Action> ACTIONS = new HashMap<>();
    private static final Map<UUID, Integer> SERIALS = new HashMap<>();
    private static UUID match;

    /** 蓄力动作本身不设固定时长（由服务器相位驱动），这里只记录「正在蓄力」。 */
    public static final long CHARGE_HINT_MS = 1000L;
    public static final long STRIKE_MS = 320L;
    public static final long MISS_MS = 900L;
    public static final long INSTINCT_MS = 600L;
    public static final long CAST_MS = EchoSkillCastAnim.CAST_TICKS * 50L;
    /** 擦刀：0.9 秒，比出击更长，命中后有清晰的「处理刀」时间。 */
    public static final long WIPE_MS = 900L;

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoHuntPayload.ID, (payload, context) -> context.client().execute(() -> {
            if (match == null || !match.equals(payload.match())) {
                match = payload.match(); ACTIONS.clear(); SERIALS.clear();
            }
            int seen = SERIALS.getOrDefault(payload.actor(), -1);
            if (payload.serial() <= seen) return;
            SERIALS.put(payload.actor(), payload.serial());
            long now = System.nanoTime() / 1_000_000L;
            long duration = switch (payload.kind()) {
                case CHARGE -> CHARGE_HINT_MS;
                case STRIKE -> STRIKE_MS;
                case MISS -> MISS_MS;
                case INSTINCT -> INSTINCT_MS;
                case CAST -> CAST_MS;
                case WIPE -> WIPE_MS;
            };
            ACTIONS.put(payload.actor(), new Action(payload.kind(), payload.castStyle(), payload.empowered(), now, duration));
        }));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ACTIONS.isEmpty()) return;
            long now = System.nanoTime() / 1_000_000L;
            ACTIONS.values().removeIf(action -> !action.alive(now));
        });
    }

    /** 当前动作；没有时返回 {@code null}。 */
    public static Action action(UUID actor) {
        if (actor == null) return null;
        Action action = ACTIONS.get(actor);
        if (action == null) return null;
        long now = System.nanoTime() / 1_000_000L;
        return action.alive(now) ? action : null;
    }

    /** 指定动作的进度；动作不匹配时返回 -1。 */
    public static float progress(UUID actor, EchoHuntPayload.Kind kind) {
        Action action = action(actor);
        if (action == null || action.kind != kind) return -1F;
        return action.progress(System.nanoTime() / 1_000_000L);
    }

    public static void clear() { ACTIONS.clear(); SERIALS.clear(); match = null; }
}