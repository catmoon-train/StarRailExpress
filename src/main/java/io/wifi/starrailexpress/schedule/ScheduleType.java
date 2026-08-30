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

package io.wifi.starrailexpress.schedule;

/**
 * 定时任务的触发类型。
 */
public enum ScheduleType {
    /** 现实时间每天 HH:mm 执行。 */
    REALTIME_DAILY,
    /** 现实时间指定星期几(1=周一..7=周日)的 HH:mm 执行。 */
    REALTIME_WEEKLY,
    /** 现实时间指定完整日期时间执行一次,触发后自动移除。 */
    REALTIME_ONCE,
    /** 现实时间每 N 秒执行一次。 */
    REALTIME_INTERVAL,
    /** 游戏时间每 N tick 执行一次,基于服务器 tick 计数,不受 /time set、睡觉影响。 */
    GAMETIME_INTERVAL,
    /** 服务器启动后执行一次。 */
    SERVER_START,
    /** 服务器结束前执行一次。 */
    SERVER_STOP,
}
