package io.wifi.starrailexpress.training;

/** 每秒最多十次控制请求，允许短时突发；不依赖墙上时钟。 */
public final class TrainingRequestLimiter {
    private long window = -1;
    private int used;
    public boolean allow(long tick) {
        long next = Math.floorDiv(tick, 20);
        if (next != window) { window = next; used = 0; }
        return used++ < 10;
    }
}
