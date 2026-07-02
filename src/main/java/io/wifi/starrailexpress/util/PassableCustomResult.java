package io.wifi.starrailexpress.util;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public class PassableCustomResult<T> {
    // 私有单例，使用通配符 ? 表示任意类型
    private static final PassableCustomResult<?> PASS = new PassableCustomResult<>(
            null, 0);

    @Nullable
    private final T value; // 改为 final，保证不可变
    public final int flag;

    private PassableCustomResult(T text, int flag) {
        this.value = text;
        this.flag = flag;
    }

    
    @SuppressWarnings("unchecked")
    public static <T> PassableCustomResult<T> pass() {
        return (PassableCustomResult<T>) PASS;
    }

    public static <T> PassableCustomResult<T> custom(T value) {
        return new PassableCustomResult<>(value, 1);
    }

    // 获取内容时，若为 TRUE/FALSE/PASS 则返回 empty，更安全
    public Optional<T> getContent() {
        return Optional.ofNullable(value);
    }


    public boolean isPass() {
        return this.flag == 0;
    }

    public boolean isCustom() {
        return this.flag == 1;
    }
}