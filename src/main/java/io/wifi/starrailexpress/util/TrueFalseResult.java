package io.wifi.starrailexpress.util;

/**
 * 掉落决策结果枚举。
 *
 * <p>
 * Enum representing the drop decision result.
 */
public enum TrueFalseResult {
    /** 强制掉落 / Force the drop to occur. */
    TRUE,
    /** 强制不掉落 / Force the drop to be prevented. */
    FALSE,
    /** 由后续监听器或默认逻辑决定 / Defer to subsequent listeners or default logic. */
    PASS
}
