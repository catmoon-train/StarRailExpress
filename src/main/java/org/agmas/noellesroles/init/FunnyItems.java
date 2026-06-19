package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.index.TMMDescItems;
import static io.wifi.starrailexpress.index.TMMItems.MISC_ITEMS_GROUP;
import static io.wifi.starrailexpress.index.TMMItems.NOELLESROLES_ALL_GROUP;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.BowenBadgeItem;
import org.agmas.noellesroles.content.item.HotPotatoItem;
import org.agmas.noellesroles.content.item.ProblemSetItem;
import org.agmas.noellesroles.content.item.ShisiyeItem;

public class FunnyItems {
  public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

  // 波纹勋章
  public static final Item HOT_POTATO = register(
      new HotPotatoItem(new Item.Properties().stacksTo(1)),
      "hot_potato");
  public static final Item BOWEN_BADGE = register(
      new BowenBadgeItem(new Item.Properties().stacksTo(1)),
      "bowen_badge");
  public static final Item SHISIYE = register(
      new ShisiyeItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)),
      "shisiye");
  public static final Item PROBLEM_SET = register(
      new ProblemSetItem(new Item.Properties().stacksTo(1)),
      "problem_set");

  @SuppressWarnings("unchecked")
  public static Item register(Item item, String id) {
    var registeredItem = registrar.create(id, item, new ResourceKey[] { MISC_ITEMS_GROUP, NOELLESROLES_ALL_GROUP });
    TMMDescItems.introItems.add(registeredItem);
    return registeredItem;
  }

  public static void init() {
    registrar.registerEntries();
  }

}