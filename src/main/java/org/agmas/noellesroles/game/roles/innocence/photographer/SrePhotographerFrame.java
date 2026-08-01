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

package org.agmas.noellesroles.game.roles.innocence.photographer;

/**
 * 鸭子接口：由 mixin 注入到 exposure 的 {@code PhotographFrameEntity}，
 * 标记该照片框是否由"摄影师"放置（用于开局只清理摄影师放置的画框）。
 */
public interface SrePhotographerFrame {
    boolean sre$isPhotographerPlaced();

    void sre$setPhotographerPlaced(boolean placed);

    /** 该画框已传送玩家的次数。 */
    int sre$getTeleportCount();

    void sre$setTeleportCount(int count);
}
