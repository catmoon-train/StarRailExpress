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

package org.agmas.noellesroles.content.item;

import net.minecraft.world.item.Item;

/**
 * 假裹尸袋
 * - 没有任何功能，仅用于迷惑敌人
 * - 继承裹尸袋的材质
 */
public class FakeBodyBagItem extends Item {
    public FakeBodyBagItem(Properties properties) {
        super(properties);
    }
}
