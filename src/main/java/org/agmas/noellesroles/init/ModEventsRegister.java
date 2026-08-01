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

package org.agmas.noellesroles.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.events.*;

import java.util.*;

/**
 * 事件注册入口 - 委托给各分类事件处理器
 */
public class ModEventsRegister {

    // ==================== 兼容字段（保持外部引用可用） ====================

    public static boolean isMJVerifyEnabled = false;
    public static List<Item> canThrowItems = new ArrayList<>();
    public static final int TRACK_DISTANCE = 8;

    // ==================== 事件注册 ====================

    public static void registerEvents() {
        NRDeathEvents.register();
        NRCombatEvents.register();
        NRGameStateEvents.register();
        NRInteractionEvents.register();
    }

    public static void registerPredicate() {
        NRRulePredicateEvents.register();

        canThrowItems = NRRulePredicateEvents.canThrowItems;
    }

    // ==================== 兼容方法（外部引用） ====================

    public static void reJudgeSpectatorsPenalty(Level level) {
        NRDeathEvents.reJudgeSpectatorsPenalty(level);
    }
}
