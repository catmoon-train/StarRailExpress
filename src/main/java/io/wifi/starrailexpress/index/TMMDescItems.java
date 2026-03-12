package io.wifi.starrailexpress.index;

import java.util.ArrayList;

import net.minecraft.world.item.Item;
public interface TMMDescItems {
   public static ArrayList<Item> introItems = new ArrayList<>();
   public static void register(){
        introItems.add(TMMItems.BAT);
        introItems.add(TMMItems.KNIFE);
        introItems.add(TMMItems.BODY_BAG);
        introItems.add(TMMItems.CROWBAR);
        introItems.add(TMMItems.DEFENSE_VIAL);
        introItems.add(TMMItems.DERRINGER);
        introItems.add(TMMItems.FIRECRACKER);
        introItems.add(TMMItems.GRENADE);
        introItems.add(TMMItems.IRON_DOOR_KEY);
        introItems.add(TMMItems.KEY);
        introItems.add(TMMItems.LETTER);
        introItems.add(TMMItems.LOCKPICK);
        introItems.add(TMMItems.NOTE);
        introItems.add(TMMItems.POISON_VIAL);
        introItems.add(TMMItems.BLACKOUT);
        introItems.add(TMMItems.PSYCHO_MODE);
        introItems.add(TMMItems.REVOLVER);
        introItems.add(TMMItems.SNIPER_RIFLE);
        introItems.add(TMMItems.SCOPE);
        introItems.add(TMMItems.MAGNUM_BULLET);
        introItems.add(TMMItems.NUNCHUCK);
   }
}