package io.wifi.srepackcompat.voice;

import java.util.concurrent.TimeUnit;

/** 每个服务器会话独立的重试状态；使用单调时钟，不受系统时间调整影响。 */
public final class VoicechatReconnectBackoff {
    private static final int[] DELAYS_SECONDS = {5, 10, 20, 30};
    private boolean previouslyConnected;
    private boolean waiting;
    private int delayIndex;
    private long deadline;

    public void connected() {
        previouslyConnected = true;
        waiting = false;
        delayIndex = 0;
    }

    public void reset() {
        previouslyConnected = false;
        pause();
    }

    /** 禁用期间不重试，重新启用后重新等待首档，但保留成功连接记录。 */
    public void pause() {
        waiting = false;
        delayIndex = 0;
    }

    public boolean shouldRetry(long now) {
        if (!previouslyConnected) return false;
        if (!waiting) {
            waiting = true;
            deadline = now + delayNanos();
            return false;
        }
        if (now - deadline < 0) return false;
        delayIndex = Math.min(delayIndex + 1, DELAYS_SECONDS.length - 1);
        deadline = now + delayNanos();
        return true;
    }

    private long delayNanos() {
        return TimeUnit.SECONDS.toNanos(DELAYS_SECONDS[delayIndex]);
    }
}
