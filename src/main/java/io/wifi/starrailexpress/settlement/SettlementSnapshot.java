package io.wifi.starrailexpress.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 停局后冻结的舞台事实；客户端只能演出，不能修改胜负或统计。
 *
 * @param mvpSkin 本局 MVP 装备的结算皮肤 ID；{@code "default"} 表示无额外布景。
 *                本体不解释这个字符串，具体表现由客户端的
 *                {@code SettlementSkins} 注册表按 ID 分发。
 */
public record SettlementSnapshot(String dimension, double x, double y, double z, String titleJson,
                                 UUID mvp, List<Actor> actors, List<Event> events, String mvpSkin) {
    /** 无额外布景的默认皮肤 ID。 */
    public static final String DEFAULT_SKIN = "default";

    /** 兼容旧快照与未指定皮肤的结算。 */
    public SettlementSnapshot(String dimension, double x, double y, double z, String titleJson,
                              UUID mvp, List<Actor> actors, List<Event> events) {
        this(dimension, x, y, z, titleJson, mvp, actors, events, DEFAULT_SKIN);
    }

    /**
     * 本局是否使用了「非默认」结算皮肤。
     * <p>
     * 本体只关心「有没有额外布景」，不再知道任何具体皮肤 ID；
     * 具体是哪一款由客户端注册表按 {@link #mvpSkin()} 解析。
     */
    public boolean hasSkin() {
        return mvpSkin != null && !mvpSkin.isBlank() && !DEFAULT_SKIN.equals(mvpSkin);
    }

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

    /** textJson 保留原回放的翻译键、职业和事件说明；item 是事件涉及的物品 ID（可为空）。 */
    public record Event(long time, String type, UUID source, UUID target, String textJson,
                        String sourceRole, String targetRole, String item) { }

    /** 事件是否与 MVP 直接相关：MVP 是发起者或目标，或 MVP 的换职事件。 */
    public static boolean involves(UUID mvp, Event event) {
        if (mvp == null || event == null) return false;
        return mvp.equals(event.source()) || mvp.equals(event.target());
    }

    /** 回放只保留 MVP 相关事件，但始终保留开局与终局两条锚点。 */
    public static List<Event> mvpTimeline(UUID mvp, List<Event> events) {
        List<Event> kept = new java.util.ArrayList<>();
        for (Event event : events) {
            if (involves(mvp, event) || "GAME_START".equals(event.type()) || "GAME_END".equals(event.type()))
                kept.add(event);
        }
        return kept;
    }
}
