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
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 聊天 HUD（头顶聊天框）相关规则。
 * 从 {@link io.wifi.starrailexpress.SRE} 的静态列表中按类别剥离归一化而来。
 */
public class ChatHudRules {
    /** 角色维度：满足任一条件的角色可使用聊天 HUD。 */
    public static List<Predicate<SRERole>> canUseChatHud = new ArrayList<>();
    /** 玩家维度：满足任一条件的玩家可使用聊天 HUD。 */
    public static List<Predicate<Player>> canUseChatHudPlayer = new ArrayList<>();
    /** 玩家维度：满足任一条件的玩家被禁止使用聊天 HUD。 */
    public static List<Predicate<Player>> cantUseChatHud = new ArrayList<>();
}
