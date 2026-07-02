package io.wifi.starrailexpress.util;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

public class TrueFalseAndCustomResult<T> {
    // 私有单例，使用通配符 ? 表示任意类型
    private static final TrueFalseAndCustomResult<?> TRUE = new TrueFalseAndCustomResult<>(
            null, 0);
    private static final TrueFalseAndCustomResult<?> FALSE = new TrueFalseAndCustomResult<>(
            null, 1);
    private static final TrueFalseAndCustomResult<?> PASS = new TrueFalseAndCustomResult<>(
            null, 2);

    @Nullable
    private final T text; // 改为 final，保证不可变
    public final int flag;

    private TrueFalseAndCustomResult(T text, int flag) {
        this.text = text;
        this.flag = flag;
    }

    // 泛型工厂方法 —— 调用时自动推断类型，无警告
    @SuppressWarnings("unchecked")
    public static <T> TrueFalseAndCustomResult<T> yes() {
        return (TrueFalseAndCustomResult<T>) TRUE;
    }

    // 泛型工厂方法 —— 调用时自动推断类型，无警告
    @SuppressWarnings("unchecked")
    public static <T> TrueFalseAndCustomResult<T> allow() {
        return (TrueFalseAndCustomResult<T>) TRUE;
    }

    @SuppressWarnings("unchecked")
    public static <T> TrueFalseAndCustomResult<T> no() {
        return (TrueFalseAndCustomResult<T>) FALSE;
    }

    @SuppressWarnings("unchecked")
    public static <T> TrueFalseAndCustomResult<T> disallow() {
        return (TrueFalseAndCustomResult<T>) FALSE;
    }

    @SuppressWarnings("unchecked")
    public static <T> TrueFalseAndCustomResult<T> pass() {
        return (TrueFalseAndCustomResult<T>) PASS;
    }

    public static <T> TrueFalseAndCustomResult<T> custom(T text) {
        return new TrueFalseAndCustomResult<>(text, 3);
    }

    // 获取内容时，若为 TRUE/FALSE/PASS 则返回 empty，更安全
    public Optional<T> getContent() {
        return Optional.ofNullable(text);
    }

    public boolean isTrue() {
        return this.flag == 0;
    }

    public boolean isFalse() {
        return this.flag == 1;
    }

    public boolean isPass() {
        return this.flag == 2;
    }

    public boolean isCustom() {
        return this.flag == 3;
    }
}