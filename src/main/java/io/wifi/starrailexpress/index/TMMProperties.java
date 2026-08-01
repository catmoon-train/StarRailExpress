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

package io.wifi.starrailexpress.index;

import io.wifi.starrailexpress.content.block.property.CouchArms;
import io.wifi.starrailexpress.content.block.property.OrnamentShape;
import io.wifi.starrailexpress.content.block.property.RailingShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public interface TMMProperties {
    BooleanProperty ACTIVE = BooleanProperty.create("active"); // whether a block is receiving power from a breaker
    BooleanProperty INTERACTION_COOLDOWN = BooleanProperty.create("interaction_cooldown");
    BooleanProperty LEFT = BooleanProperty.create("left");
    BooleanProperty OPAQUE = BooleanProperty.create("opaque");
    BooleanProperty RIGHT = BooleanProperty.create("right");
    BooleanProperty SUPPORT = BooleanProperty.create("support");
    BooleanProperty TOP = BooleanProperty.create("top");

    EnumProperty<CouchArms> COUCH_ARMS = EnumProperty.create("arms", CouchArms.class);
    EnumProperty<OrnamentShape> ORNAMENT_SHAPE = EnumProperty.create("shape", OrnamentShape.class);
    EnumProperty<RailingShape> RAILING_SHAPE = EnumProperty.create("shape", RailingShape.class);
}
