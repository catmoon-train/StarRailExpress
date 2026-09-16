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

package io.wifi.starrailexpress.client.gui.widget;

/**
 * 开关的三种状态：开 / 关 / 未设置。
 *
 * <p>
 * 用枚举而不是可空的 {@code Boolean}：开关的状态、状态词函数、点击回调全都只在这三种值里取，
 * <b>不可能出现 null</b>，量宽度 / 渲染都不用再防「调用方没处理 null」这类崩溃；
 * 而且状态词函数里写 {@code switch} 表达式时编译器会强制覆盖三种状态，漏一种直接编译不过。
 *
 * <p>
 * 「未设置」用于三态开关（例如修饰符的阵营限制：不限 / 仅给 / 不给）；只有真 / 假两种状态的开关
 * 用 {@link #toggled()}（开 ⇄ 关），永远走不到「未设置」。
 */
public enum SwitchState {
    /** 开（绿 ✓）。 */
    ON,
    /** 关（红 ✗）。 */
    OFF,
    /** 未设置（土褐色短横 -）。 */
    UNSET;

    /** 是不是「开」。 */
    public boolean isOn() {
        return this == ON;
    }

    /** 是不是「未设置」。 */
    public boolean isUnset() {
        return this == UNSET;
    }

    /** 是 / 否 → 开关状态。 */
    public static SwitchState of(boolean value) {
        return value ? ON : OFF;
    }

    /** 可空的「是 / 否」→ 开关状态（{@code null} = 未设置）。数据层用可空 Boolean 表达三态时用它换算。 */
    public static SwitchState of(Boolean value) {
        if (value == null) {
            return UNSET;
        }
        return value.booleanValue() ? ON : OFF;
    }

    /** 给数据层用：开 = true、关 = false、未设置 = null。 */
    public Boolean toBoolean() {
        return switch (this) {
            case ON -> Boolean.TRUE;
            case OFF -> Boolean.FALSE;
            case UNSET -> null;
        };
    }

    /** 两态开关的循环：开 ⇄ 关。 */
    public SwitchState toggled() {
        return this == ON ? OFF : ON;
    }

    /** 三态开关的循环：未设置 → 开 → 关 → 未设置。 */
    public SwitchState next() {
        return switch (this) {
            case UNSET -> ON;
            case ON -> OFF;
            case OFF -> UNSET;
        };
    }
}
