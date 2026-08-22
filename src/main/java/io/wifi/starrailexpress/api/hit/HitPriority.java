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

package io.wifi.starrailexpress.api.hit;

/**
 * 瞄准优先级。
 *
 * <p>{@link #PRIMARY} 与玩家同级，会挡住子弹；{@link #FALLBACK} 仅在射线未命中
 * 玩家 / PRIMARY 目标时才拾取（例如设陷者绊线，避免挡在玩家前面抢走子弹）。
 */
public enum HitPriority {
    PRIMARY,
    FALLBACK
}
