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
