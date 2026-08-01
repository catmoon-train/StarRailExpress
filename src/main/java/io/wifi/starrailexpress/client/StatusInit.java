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

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StatusInit {
   public record StatusBar(String id, String name, Supplier<Float> progressSupplier){

   }
    public static Map<String, StatusBar> statusBars = new HashMap<>();

   static {
       statusBars.put("Psycho", new StatusBar("Psycho", "\u00a76狂暴模式", () -> {
           final var playerPsychoComponent = SREPlayerPsychoComponent.KEY.get(Minecraft.getInstance().player);
           if (playerPsychoComponent == null){
               return 0.0f;
           }
           if (playerPsychoComponent.getPsychoTicks() <= 0) return 0.0f;
           return playerPsychoComponent.getPsychoTicks() / (float) GameConstants.getPsychoTimer();
       }));
   }

   public static StatusBar getStatusBar(String id) {
       return statusBars.get(id);
   }
}
