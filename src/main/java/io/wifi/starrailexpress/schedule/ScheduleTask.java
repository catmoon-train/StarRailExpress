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

import java.util.ArrayList;
import java.util.List;

/**
 * 一条定时任务配置,直接由 Gson 序列化/反序列化。
 * transient 字段为运行时状态,不写入 json 文件。
 */
public class ScheduleTask {

    /** 任务唯一标识。 */
    public String id;
    /** 触发类型。 */
    public ScheduleType type;
    /** 要执行的 mcfunction 的 ResourceLocation 字符串,如 "starrailexpress:foo"。 */
    public String function;
    /** REALTIME_DAILY / REALTIME_WEEKLY 的小时(0-23)。 */
    public int hour;
    /** REALTIME_DAILY / REALTIME_WEEKLY 的分钟(0-59)。 */
    public int minute;
    /** REALTIME_WEEKLY 的星期(1=周一..7=周日)。 */
    public List<Integer> days = new ArrayList<>();
    /** REALTIME_ONCE 的完整日期时间,格式 "yyyy-MM-dd HH:mm"。 */
    public String datetime;
    /** REALTIME_INTERVAL 的间隔秒数。 */
    public long intervalSeconds;
    /** GAMETIME_INTERVAL 的间隔 tick 数。 */
    public long intervalTicks;

    /** 下一次现实时间触发的 epoch 毫秒,运行时状态。 */
    public transient long nextRunAtMillis;
    /** 下一次游戏时间触发的 tick,运行时状态。 */
    public transient long nextRunAtTick;

    /** 将缺失字段归一化为安全默认值。 */
    public ScheduleTask normalized() {
        if (id == null) {
            id = "";
        }
        if (type == null) {
            type = ScheduleType.REALTIME_DAILY;
        }
        if (function == null) {
            function = "";
        }
        if (days == null) {
            days = new ArrayList<>();
        }
        return this;
    }
}
