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

package io.wifi.starrailexpress.rules;

import io.wifi.starrailexpress.api.SRERole;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 角色可见性 / 互动相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class RoleVisibilityRules {
    /** 满足任一条件的角色可以看到 / 使用其他玩家（无视常规限制）。 */
    public static List<Predicate<SRERole>> canUseOtherPerson = new ArrayList<>();
}
