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

package org.agmas.harpymodloader.modifiers;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;

public class HMLModifiers {

    public static final ArrayList<SREModifier> MODIFIERS = new ArrayList<>();

    public static SREModifier getModifier(ResourceLocation res) {
        if (res == null)
            return null;
        for (var m : MODIFIERS) {
            if (m.identifier().equals(res))
                return m;
        }
        return null;
    }

    public static void init() {
    }

    public static SREModifier register(SREModifier modifier) {
        return registerModifier(modifier);
    }

    public static SREModifier register(SREModifier modifier, String... flags) {
        return registerModifier(modifier, flags);
    }

    public static SREModifier registerModifier(SREModifier modifier, String... flags) {
        return registerModifier(modifier.addFlag(flags));
    }

    public static SREModifier registerModifier(SREModifier modifier) {
        MODIFIERS.add(modifier);
        return modifier;
    }

    public static HashSet<String> getAllFlags() {
        HashSet<String> filters = new HashSet<>();
        for (var it : MODIFIERS) {
            filters.addAll(it.getFlags());
        }
        return filters;
    }
}
