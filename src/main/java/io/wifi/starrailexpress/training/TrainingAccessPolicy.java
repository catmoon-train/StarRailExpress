package io.wifi.starrailexpress.training;

/**
 * 可独立测试的训练权限边界，不能由客户端提交的 active 标记决定。
 * 集成服开放到局域网后场地依旧有效，因此权限只看训练会话标记、非专服、集成服主人和所在维度。
 */
public final class TrainingAccessPolicy {
    private TrainingAccessPolicy() { }
    public static boolean permits(boolean marked, boolean dedicated, boolean owner, boolean sameWorld) {
        return marked && !dedicated && owner && sameWorld;
    }
    public static boolean permitsDedicated(boolean enabled, boolean leased, boolean owner, boolean sameWorld) {
        return enabled && leased && owner && sameWorld;
    }
}
