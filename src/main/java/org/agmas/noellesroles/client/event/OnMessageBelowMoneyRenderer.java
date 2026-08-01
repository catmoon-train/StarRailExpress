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
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnMessageBelowMoneyRenderer {

    Event<OnMessageBelowMoneyRenderer> EVENT = createArrayBacked(OnMessageBelowMoneyRenderer.class,
            listeners -> (client, guiGraphics, deltaTracker) -> {
                MutableComponentResult a = new MutableComponentResult();
                for (OnMessageBelowMoneyRenderer listener : listeners) {

                    var res = listener.onRenderer(client, guiGraphics, deltaTracker);
                    if (res != null && res.singleContent != null)
                        a.mutipleContent.add(res.singleContent);
                }
                return a;
            });

    MutableComponentResult onRenderer(Minecraft client, FakeGuiGraphics guiGraphics, DeltaTracker deltaTracker);
}