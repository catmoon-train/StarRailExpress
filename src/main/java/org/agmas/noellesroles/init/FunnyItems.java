package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
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
import org.agmas.noellesroles.item.*;

public class FunnyItems {
  public static ResourceKey<CreativeModeTab> MISC_CREATIVE_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
      Noellesroles.id("funny"));
  public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

  // 波纹勋章
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
    // Create the identifier for the item.
    // Register the item.
    var registeredItem = registrar.create(id, item, new ResourceKey[] { MISC_CREATIVE_GROUP });
    // Item registeredItem = Registry.register(BuiltInRegistries.ITEM, itemID,
    // item);

    // Return the registered item!
    return registeredItem;
  }

  public static void init() {
    registrar.registerEntries();
    Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MISC_CREATIVE_GROUP, FabricItemGroup.builder()
        .title(Component.translatable("item_group.noellesroles.funny")).icon(() -> {
          return new ItemStack(FunnyItems.PROBLEM_SET);
        }).build());
  }

}