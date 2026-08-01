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

package net.exmo.sre.mod_whitelist.common;

import java.io.Serializable;

/**
 * Represents a shader pack's information including its ID and SHA256 hash
 */
public record ShaderPackInfo(String packId, String sha256) implements Serializable {
	
	public ShaderPackInfo {
		if (packId == null || packId.isEmpty()) {
			throw new IllegalArgumentException("packId cannot be null or empty");
		}
		if (sha256 == null || sha256.isEmpty()) {
			throw new IllegalArgumentException("sha256 cannot be null or empty");
		}
	}

	@Override
	public String toString() {
		return "ShaderPackInfo{" +
				"packId='" + packId + '\'' +
				", sha256='" + sha256 + '\'' +
				'}';
	}
}