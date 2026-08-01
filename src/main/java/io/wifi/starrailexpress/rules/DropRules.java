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

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 物品丢弃相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class DropRules {
    /** 允许被丢弃的物品 ID 列表（字符串形式）。 */
    public static ArrayList<String> canDropItem = new ArrayList<>();
    /** 满足任一条件的玩家允许丢弃物品。 */
    public static ArrayList<Predicate<Player>> canDrop = new ArrayList<>();
}
