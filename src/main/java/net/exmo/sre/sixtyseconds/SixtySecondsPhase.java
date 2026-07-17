package net.exmo.sre.sixtyseconds;

/**
 * 末日60秒模式相位：
 * INACTIVE → PREPARATION(90s 家中搜物资) → DAY(7×8min 游戏日) → FINISHED → stopGame。
 */
public enum SixtySecondsPhase {
    INACTIVE,
    PREPARATION,
    DAY,
    FINISHED
}
