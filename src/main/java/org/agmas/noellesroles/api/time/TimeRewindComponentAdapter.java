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

package org.agmas.noellesroles.api.time;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.ladysnake.cca.api.v3.component.Component;

/**
 * Overrides the default snapshot format for one CCA component type.
 *
 * <p>Use this when neither the normal CCA persistent NBT nor
 * {@code RoleComponent}'s synchronization NBT represents all rewindable state.
 * Implementations run on the server thread and must not retain the passed tag.
 */
public interface TimeRewindComponentAdapter<C extends Component> {
    void writeSnapshot(C component, CompoundTag tag, HolderLookup.Provider registryLookup);

    void readSnapshot(C component, CompoundTag tag, HolderLookup.Provider registryLookup);

    /**
     * Restore order for adapters with dependencies on other CCA components.
     * Higher values are restored later. Most adapters should keep zero.
     */
    default int restorePriority() {
        return 0;
    }
}
