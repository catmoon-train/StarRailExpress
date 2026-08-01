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

package org.agmas.noellesroles.utils;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;

public class LocalDateData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int day;
    private int month;
    private int year;

    public LocalDateData() {
        // 使用系统默认时区
        LocalDate today = LocalDate.now();
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }

    // 指定时区
    public LocalDateData(ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }

    // 指定时区字符串
    public LocalDateData(String timezone) {
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        this.day = today.getDayOfMonth();
        this.month = today.getMonthValue();
        this.year = today.getYear();
    }

    // getters
    public int getDay() {
        return day;
    }

    public int getMonth() {
        return month;
    }

    public int getYear() {
        return year;
    }
}