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

package io.wifi.starrailexpress.index.tag;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public interface TMMBlockTags {

    TagKey<Block> BRANCHES = create("branches");
    TagKey<Block> VENT_SHAFTS = create("vent_shafts");
    TagKey<Block> VENT_HATCHES = create("vent_hatches");
    TagKey<Block> WALKWAYS = create("walkways");
    TagKey<Block> SPRINKLERS = create("sprinklers");

    private static TagKey<Block> create(String id) {
        return TagKey.create(Registries.BLOCK, SRE.TMMId(id));
    }
}
