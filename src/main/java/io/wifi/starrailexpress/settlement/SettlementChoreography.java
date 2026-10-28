package io.wifi.starrailexpress.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 舞台站位和出场节拍的唯一来源；不依赖客户端或注册表，可独立验证。 */
public final class SettlementChoreography {
    /** 尸体直接堆在舞台黑地板上，不额外抬台。 */
    public static final double REAR_FLOOR = 0;
    public static final double PILE_Z = 6.0;
    /** 胜方约在 130 tick 全部落地并摆好动作，尸体随后才开始下落。 */
    public static final int DROP_START = 150;
    private SettlementChoreography() { }

    public record Slot(double x, double y, double z) { }

    /** 单人站位间距；比之前更松，避免角色挤成一团。 */
    public static final double WINNER_SPACING = 1.35;

    /**
     * 胜方站位：存活者居中、已死亡者排到两侧。
     * <p>索引 0 视为 MVP（站在正中），其余按 {@link #winnerOrder} 排出的顺序依次向外铺开。</p>
     */
    public static Slot winner(int index, int count) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("无效胜方序号");
        double spacing = Math.min(WINNER_SPACING, 12.0 / Math.max(1, count - 1));
        double x = spread(index) * spacing;
        // 人数多时只把最外侧的人放到后排，保证仍最多两排。
        boolean back = count > 9 && Math.abs(x) > spacing * 2.6;
        return new Slot(x, back ? .5 : 0, back ? 1.25 : -.35);
    }

    /**
     * 排序胜方：MVP 第一，然后存活者由内向外，最后是已死亡者。
     * <p>这样中间站的都是活着的人，死亡者被推到两边。</p>
     */
    public static List<SettlementSnapshot.Actor> winnerOrder(List<SettlementSnapshot.Actor> winners, UUID mvp) {
        List<SettlementSnapshot.Actor> ordered = new ArrayList<>();
        SettlementSnapshot.Actor center = null;
        for (var actor : winners) if (mvp != null && mvp.equals(actor.id())) center = actor;
        if (center != null) ordered.add(center);
        for (var actor : winners) if (actor != center && !actor.dead()) ordered.add(actor);
        for (var actor : winners) if (actor != center && actor.dead()) ordered.add(actor);
        return ordered;
    }

    /**
     * 由内向外对称铺开：0 居中，1 左、2 右、3 再外左、4 再外右……
     * <p>序号越大离中心越远，配合 {@link #winnerOrder} 就能把死亡者稳定推到两侧。</p>
     */
    private static double spread(int index) {
        if (index == 0) return 0;
        double ring = (index + 1) / 2.0;
        return index % 2 == 1 ? -ring : ring;
    }

    /** 尸堆承接区的半宽/半深：收得很紧，少量尸体也会互相压住形成高度。 */
    public static double pileHalfWidth(int count) {
        return Math.min(1.15, .50 + Math.sqrt(Math.max(1, count)) * .11);
    }

    public static double pileHalfDepth(int count) {
        return Math.min(1.00, .46 + Math.sqrt(Math.max(1, count)) * .10);
    }

    /** 后台只在落下时出现尸体；少量逐个入场，大量有界分批，开场内全部释放。 */
    public static int dropTick(int index, int count) {
        int interval = Math.max(0, Math.min(8, 75 / Math.max(1, count)));
        return DROP_START + index * interval;
    }

    /** 从中央一个窄口依次投放，尸体互相压住；位置只决定下落顺序而非最终摆放。 */
    public static Slot drop(int index, int count) {
        double angle = index * 2.399963229728653;
        double radius = Math.min(.55, .18 + index * .035);
        return new Slot(Math.cos(angle) * radius, 9.0 + index * .22,
                PILE_Z + Math.sin(angle) * radius * .8);
    }

    /**
     * 只有“获胜阵营里已死亡的人”才变黑。
     * <p>落败者本身就在后排尸体堆里，保持原本皮肤颜色；存活者无论阵营都不变黑。</p>
     */
    public static boolean silhouette(boolean winner, boolean dead) {
        return winner && dead;
    }
}
