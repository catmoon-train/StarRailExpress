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
 * 反重力方块：透明、无碰撞的能量场。
 *
 * <p>玩家穿过（接触）方块时由 {@code GravityFlipManager} 翻转其重力方向：
 * 同一玩家两次触发之间有 2.5 秒冷却；因反重力向天上坠落超过 30 格会自动解除。
 *
 * <p>方块本身只承担「可见的能量场」职责，判定逻辑集中在管理器里按 tick 扫描，
 * 这样无碰撞的方块也能稳定收到接触事件。
 */
public class AntiGravityBlock extends Block {

    public AntiGravityBlock(Properties properties) {
        super(properties.noOcclusion().noCollission());
    }
}
