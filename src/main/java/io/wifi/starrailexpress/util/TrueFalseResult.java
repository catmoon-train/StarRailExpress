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
