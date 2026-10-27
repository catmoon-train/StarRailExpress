package io.wifi.starrailexpress.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 停局后冻结的舞台事实；客户端只能演出，不能修改胜负或统计。 */
public record SettlementSnapshot(String dimension, double x, double y, double z, String titleJson,
                                 UUID mvp, List<Actor> actors, List<Event> events) {
    public static final int INTRO_TICKS = 320;
    public static final int HOLD_TICKS = 30;
    public static final int SKIP_AFTER_TICKS = 100;

    public int eventTicks() {
        return Math.max(3, Math.min(30, 1800 / Math.max(1, events.size())));
    }

    public int durationTicks() {
        return INTRO_TICKS + events.size() * eventTicks() + 80;
    }

    public static UUID selectMvp(List<Actor> actors) {
        return actors.stream().filter(Actor::winner)
                .sorted(Comparator.comparingInt(Actor::score).reversed()
                        .thenComparing(Comparator.comparingInt(Actor::kills).reversed())
                        .thenComparing(actor -> actor.id().toString()))
                .map(Actor::id).findFirst().orElse(null);
    }

    public record Actor(UUID id, String name, String role, int color, boolean winner, boolean dead,
                        boolean killer, int kills, int tasks) {
        public int score() { return kills + (killer ? 0 : tasks); }
    }

    /** textJson 保留原回放的翻译键、职业和事件说明；时间是自开局起的毫秒。 */
    public record Event(long time, String type, UUID source, UUID target, String textJson,
                        String sourceRole, String targetRole) { }
}
