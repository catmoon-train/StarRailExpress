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

package io.wifi.starrailexpress.compat.sodium;

import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;

/**
 * 混入接口：由 DefaultShaderInterface 实现，用于向着色器绑定景色偏移 UBO。
 * 参考 wathe 的 SodiumShaderInterface，适配 SRE 的命名空间。
 */
public interface SRESceneryShaderInterface {
    void sre$setSceneryOffsets(GlMutableBuffer buffer);
}