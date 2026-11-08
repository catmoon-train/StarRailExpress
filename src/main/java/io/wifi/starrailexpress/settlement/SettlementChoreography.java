package io.wifi.starrailexpress.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 舞台站位和出场节拍的唯一来源；不依赖客户端或注册表，可独立验证。 */
public final class SettlementChoreography {
    /** 尸体直接堆在舞台黑地板上，不额外抬台。 */
    public static final double REAR_FLOOR = 0;
    public static final double PILE_Z = 6.0;
    /** 后台尸体在胜方完成吊线下降并开始庆祝后入场。 */
    public static final int DROP_START = 150;
    private SettlementChoreography() { }

    public record Slot(double x, double y, double z) { }

    /** 常规人数下保持完整人物间距，不再随总人数直接压缩。 */
    public static final double WINNER_SPACING = 1.35;

    /**
     * 胜方使用错位的两排合照；MVP 正中、同伴向两侧铺开，后排插在前排的空隙中。
     * <p>两排都落在真实地板上，不通过悬空半格假装抬台。超大阵容仍限制在舞台内。</p>
     */
    public static Slot winner(int index, int count) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("无效胜方序号");
        int front = count <= 9 ? count : (((count + 1) / 2) | 1);
        int back = count - front;
        double spacing = Math.min(WINNER_SPACING, 22.0 / Math.max(1, Math.max(front - 1, back)));
        if (index < front) return new Slot(spread(index) * spacing, 0, -.35);
        int rear = index - front;
        double x = (rear / 2 + .5) * (rear % 2 == 0 ? -1 : 1) * spacing;
        return new Slot(x, 0, 1.45);
    }

    /** 根据演员实际宽度和屏幕比例取景，给姓名、侧边墨线留出安全空间。 */
    public static double cameraDistance(double halfWidth, double aspect) {
        double distance = Math.max(6.5, 6.2 + halfWidth * .25);
        return Math.min(13.0, Math.max(distance, (halfWidth + .9) / Math.max(.5, aspect)));
    }

    public static double cameraFov(double halfWidth, double aspect) {
        double distance = cameraDistance(halfWidth, aspect);
        return Math.max(44, Math.min(110, Math.toDegrees(2 * Math.atan(
                (halfWidth + .9) / (distance * Math.max(.5, aspect))))));
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
        double ring = (index + 1) / 2;
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
