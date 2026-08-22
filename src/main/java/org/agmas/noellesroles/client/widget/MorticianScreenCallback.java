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

package org.agmas.noellesroles.client.widget;

import java.util.UUID;

/**
 * 葬仪屏幕回调接口
 * 用于BodymakerRoleScreenExtension（葬仪背包界面扩展）中的多阶段选择流程
 */
public interface MorticianScreenCallback {
    /**
     * 设置选中的玩家
     */
    void setSelectedPlayer(UUID uuid);
    
    /**
     * 设置选中的死亡原因
     */
    void setSelectedDeathReason(String deathReason);
}
