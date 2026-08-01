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

package io.wifi.starrailexpress.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

public class TooltipUtil {
    public static List<Component> sprit(MutableComponent component){
        var strings = component.getString().split("\n");
        List<Component> list = new ArrayList<>();
        for (String string : strings) {
            list.add(Component.literal(string));
        }

        return list;
    }
    public static List<Component> sprit(Component component){
        var strings = component.getString().split("\n");
        List<Component> list = new ArrayList<>();
        for (String string : strings) {
            list.add(Component.literal(string));
        }
        return list;
    }
}
