package io.wifi.starrailexpress.util;

/**
 * 是否结果
 */
public enum TrueFalseResult {
    /** 是 */
    TRUE,
    /** 否 */
    FALSE,
    /** 由后续监听器或默认逻辑决定 */
    PASS;

    public boolean isPass() {
        return this == PASS;
    }
    
    public boolean isTrue() {
        return this == TRUE;
    }
    
    public boolean isFalse() {
        return this == FALSE;
    }
}
