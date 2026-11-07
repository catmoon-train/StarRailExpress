package io.wifi.starrailexpress.training;

/** 可独立测试的训练权限边界，不能由客户端提交的 active 标记决定。 */
public final class TrainingAccessPolicy {
    private TrainingAccessPolicy() { }
    public static boolean permits(boolean marked, boolean dedicated, boolean published, boolean owner, boolean sameWorld) {
        return marked && !dedicated && !published && owner && sameWorld;
    }
    public static boolean permitsDedicated(boolean enabled, boolean leased, boolean owner, boolean sameWorld) {
        return enabled && leased && owner && sameWorld;
    }
}
