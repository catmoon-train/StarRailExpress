package io.wifi.starrailexpress.settlement;

/** 舞台站位和出场节拍的唯一来源；不依赖客户端或注册表，可独立验证。 */
public final class SettlementChoreography {
    /** 尸体直接堆在舞台黑地板上，不额外抬台。 */
    public static final double REAR_FLOOR = 0;
    public static final double PILE_Z = 6.0;
    /** 胜方约在 130 tick 全部落地并摆好动作，尸体随后才开始下落。 */
    public static final int DROP_START = 150;
    private SettlementChoreography() { }

    public record Slot(double x, double y, double z) { }

    /** MVP 是索引 0，前排正中；七人以内一排，更多人两排错位，不再铺满整个舞台。 */
    public static Slot winner(int index, int count) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("无效胜方序号");
        int front = count <= 7 ? count : (count + 1) / 2;
        boolean back = index >= front;
        int rowCount = back ? count - front : front;
        int local = back ? index - front : index;
        double spacing = Math.min(1.05, 10.0 / Math.max(1, rowCount - 1));
        double x = back ? (local - (rowCount - 1) * .5) * spacing : centerOut(local) * spacing;
        // 后排稍高，保证两排都看得见；前排保持舞台地面。
        return new Slot(x, back ? .5 : 0, back ? 1.35 : -.35);
    }

    private static double centerOut(int index) {
        return index == 0 ? 0 : ((index + 1) / 2) * (index % 2 == 1 ? -1 : 1);
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
