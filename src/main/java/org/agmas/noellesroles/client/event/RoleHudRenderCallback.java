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

package org.agmas.noellesroles.client.event;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class RoleHudRenderCallback {
    public static class CustomRenderEvent<T> {
        public HashMap<T, ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>>> role_events = new HashMap<>();

        public ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>> getConsumer(T identifier) {
            return role_events.get(identifier);
        }

        public void register(T identifier, BiConsumer<FakeGuiGraphics, DeltaTracker> consumer) {
            role_events.computeIfAbsent(identifier, (a) -> {
                return new ArrayList<BiConsumer<FakeGuiGraphics, DeltaTracker>>();
            });
            role_events.get(identifier).add(consumer);
        }

        public boolean removeConsumer(ResourceLocation identifier) {
            if (role_events.containsKey(identifier)) {
                role_events.remove(identifier);
                return true;
            }
            return false;
        }
    }

    public final static CustomRenderEvent<ResourceLocation> EVENT = new CustomRenderEvent<>();
}