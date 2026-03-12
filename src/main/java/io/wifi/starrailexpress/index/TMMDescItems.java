package io.wifi.starrailexpress.index;

import java.util.ArrayList;

import net.minecraft.world.item.Item;
public interface TMMDescItems {
   public static ArrayList<Item> introItems = new ArrayList<>();
   public static void register(){
        introItems.add(TMMItems.BAT);
        introItems.add(TMMItems.KNIFE);
   }
}