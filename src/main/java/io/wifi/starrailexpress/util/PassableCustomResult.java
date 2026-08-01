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