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

package org.agmas.noellesroles.content.block.scene;

import net.minecraft.world.level.block.Block;

/**
 * 雕像/玩偶（场景任务「祷告任务」）：玩家持续看向它 5 秒完成。祷告检测在 SceneTaskManager 中通过视线射线判定。
 * 原版雕纹石英块贴图。
 */
public class StatueBlock extends Block {

    public StatueBlock(Properties settings) {
        super(settings);
    }
}
