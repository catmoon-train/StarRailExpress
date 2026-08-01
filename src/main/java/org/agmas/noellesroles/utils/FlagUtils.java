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

package org.agmas.noellesroles.utils;

import org.agmas.harpymodloader.modifiers.HMLModifiers;

import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.network.chat.Component;

import java.text.Collator;
import java.util.*;

public class FlagUtils {
    public static HashSet<String> getAllFlags() {
        HashSet<String> set = new HashSet<String>();
        set.addAll(getAllRoleFlags());
        set.addAll(getAllModifierFlags());
        return set;
    }

    public static Collator COLLATOR = Collator.getInstance();
    public static Comparator<String> comparator = (a, b) -> {
        if (a.startsWith("inner.") && !b.startsWith("inner."))
            return -1;
        if (!a.startsWith("inner.") && b.startsWith("inner."))
            return 1;
        String aText = getFlagName(a).getString();
        String bText = getFlagName(b).getString();
        return COLLATOR.compare(aText, bText);
    };

    public static LinkedHashSet<String> getAllFlagsSorted() {
        ArrayList<String> list = new ArrayList<>(getAllFlags());
        list.sort(comparator);
        return new LinkedHashSet<>(list);
    }

    public static Component getFlagName(String flag) {
        if (flag.isBlank())
            return Component.empty();
        String path = "screen.roleintroduce.flag." + flag;
        return Component.translatableWithFallback(path, flag.toUpperCase().replaceAll("_", " "));
    }

    public static HashSet<String> getAllRoleFlags() {
        return TMMRoles.getAllFlags();
    }

    public static HashSet<String> getAllModifierFlags() {
        return HMLModifiers.getAllFlags();
    }
}
