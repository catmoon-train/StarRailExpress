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

package io.wifi.starrailexpress.scenery;

import net.minecraft.world.phys.AABB;

import java.util.List;

public record SceneAsset(
        int schema,
        String minecraftVersion,
        String registryFingerprint,
        AABB sourceArea,
        List<SectionData> sections) {

    public static final int CURRENT_SCHEMA = 2;

    public SceneAsset {
        sections = List.copyOf(sections);
    }

    public record SectionData(
            int sectionX,
            int sectionY,
            int sectionZ,
            byte[] sectionPayload,
            byte[] skyLight,
            byte[] blockLight) {
        public SectionData {
            sectionPayload = sectionPayload.clone();
            skyLight = skyLight.clone();
            blockLight = blockLight.clone();
        }
    }
}
