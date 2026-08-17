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

package org.agmas.noellesroles.client;

import org.agmas.noellesroles.init.ModItems;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.util.FlashlightInterface;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class DynamiclightsEntry implements DynamicLightsInitializer {

    public static DynamicLightsContext context;

    public static void registerClientEvents() {
        ClientTickEvents.START_WORLD_TICK.register(level -> {
            for (var entity : level.entitiesForRendering()) {
                if (entity instanceof Player player) {
                    if (player.isSpectator())
                        continue;
                    if (!(player instanceof FlashlightInterface holder))
                        return;
                    var item = player.getItemInHand(InteractionHand.MAIN_HAND);

                    if (item.is(ModItems.FLASHLIGHT) && item.getOrDefault(SREDataComponentTypes.STATUS, false)) {
                        // 有光
                        if (holder.getFlashlight() == null) {
                            holder.setFlashlight(new FlashlightLightProvider(entity));
                            context.dynamicLightBehaviorManager().add(holder.getFlashlight());
                        }
                    } else {
                        // Ahw...
                        if (holder.getFlashlight() != null) {
                            context.dynamicLightBehaviorManager().remove(holder.getFlashlight());
                            holder.setFlashlight(null);
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
    }

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext ctx) {
        context = ctx;
    }

}
