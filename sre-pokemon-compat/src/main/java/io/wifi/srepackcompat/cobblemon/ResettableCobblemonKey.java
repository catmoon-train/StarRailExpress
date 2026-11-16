package io.wifi.srepackcompat.cobblemon;

/** 清除长按状态，不触发上游 onRelease 的发包或开界面逻辑。 */
public interface ResettableCobblemonKey {
    void sre$resetHeldKey();
}
