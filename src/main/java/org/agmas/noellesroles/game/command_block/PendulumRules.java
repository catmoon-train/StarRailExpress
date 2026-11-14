package org.agmas.noellesroles.game.command_block;

/** 钟摆的纯时间规则，便于独立验证回溯与可恢复充能。 */
public final class PendulumRules {
    public static final int MAX_CHARGES = 3;
    public static final int RECHARGE_TICKS = 20 * 20;

    private PendulumRules() {
    }

    /** 每次受击扣除三秒回溯时间，不会把玩家送到本次轨迹之外。 */
    public static long rewindTicks(long elapsed, long hits) {
        elapsed = Math.max(0L, elapsed);
        hits = Math.max(0L, hits);
        return hits > elapsed / 60L ? 0L : Math.max(0L, elapsed - hits * 60L);
    }

    /** 缺少多层时依次恢复，每二十秒补一层；继续释放不会重置正在恢复的计时。 */
    public record Charges(int count, long readyAt) {
        public Charges recover(long now) {
            int restored = count;
            long next = readyAt;
            while (restored < MAX_CHARGES && next > 0L && now >= next) {
                restored++;
                next = restored == MAX_CHARGES ? 0L : next + RECHARGE_TICKS;
            }
            return restored == count ? this : new Charges(restored, next);
        }

        public Charges spend(long now) {
            Charges current = recover(now);
            if (current.count == 0) {
                return current;
            }
            return new Charges(current.count - 1,
                    current.count == MAX_CHARGES ? now + RECHARGE_TICKS : current.readyAt);
        }
    }
}
