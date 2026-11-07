package io.wifi.srepackcompat.render;

import java.util.concurrent.locks.LockSupport;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** 帧节奏控制：较长余量休眠，最后 1ms 短自旋；每帧以实际完成时间作为下次基准。 */
public final class HybridFramePacer {
    private static final long SPIN_WINDOW_NANOS = 1_000_000L;
    private final LongSupplier clock;
    private final LongConsumer park;
    private long lastPaceTime;

    public HybridFramePacer() { this(System::nanoTime, LockSupport::parkNanos); }

    HybridFramePacer(LongSupplier clock, LongConsumer park) {
        this.clock = clock;
        this.park = park;
        lastPaceTime = clock.getAsLong();
    }

    public void pace(int fps) {
        long now = clock.getAsLong();
        if (fps <= 0) {
            lastPaceTime = now;
            return;
        }
        long deadline = lastPaceTime + Math.max(1L, 1_000_000_000L / fps);
        long remaining;
        while ((remaining = deadline - now) > 0 && !Thread.currentThread().isInterrupted()) {
            if (remaining > SPIN_WINDOW_NANOS) park.accept(remaining - SPIN_WINDOW_NANOS);
            else Thread.onSpinWait();
            // park 允许提前唤醒，不能假定一次休眠就到了目标时间。
            now = clock.getAsLong();
        }
        lastPaceTime = now;
    }
}
