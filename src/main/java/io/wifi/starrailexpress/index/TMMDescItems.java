package io.wifi.starrailexpress.index;

import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.LinkedHashSet;

public interface TMMDescItems {
   public static HashSet<Item> introItems = new LinkedHashSet<>();

   public static void register() {
      introItems.add(TMMItems.BAT);
      introItems.add(TMMItems.LETTER);
      introItems.add(TMMItems.KNIFE);
      introItems.add(TMMItems.BODY_BAG);
      introItems.add(TMMItems.CROWBAR);
      introItems.add(TMMItems.DEFENSE_VIAL);
      introItems.add(TMMItems.DERRINGER);
      introItems.add(TMMItems.FIRECRACKER);
      introItems.add(TMMItems.GRENADE);
      introItems.add(TMMItems.STANDARD_REVOLVER);
      introItems.add(TMMItems.STICKY_GRENADE);
      introItems.add(TMMItems.TIMED_GRENADE);
      introItems.add(TMMItems.IRON_DOOR_KEY);
      introItems.add(TMMItems.KEY);
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
      introItems.add(TMMItems.DISGUISE_1);
      introItems.add(TMMItems.DISGUISE_2);
      introItems.add(TMMItems.DISGUISE_3);
      introItems.add(TMMItems.ADMISSION_TICKET);
      introItems.add(TMMItems.SCORPION);
      introItems.add(TMMItems.EMOJI_HELMET);
      introItems.add(TMMItems.DRAWING_BOARD);
      introItems.add(SREBlocks.REMOTE_REDSTONE.asItem());
      introItems.add(SREBlocks.TRAIN_LIGHT.asItem());
   }
}