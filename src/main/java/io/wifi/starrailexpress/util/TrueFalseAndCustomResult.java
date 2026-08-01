/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.util;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

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