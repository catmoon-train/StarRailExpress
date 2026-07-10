package io.wifi.starrailexpress.index.wathe_bridge;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.impl.GrenadeChargeableItem;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import io.wifi.starrailexpress.content.item.*;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;

public interface WatheBridgerItems {
    public static ItemRegistrar registrar = new ItemRegistrar(SRE.WATHE_MOD_ID);

//     ResourceKey<CreativeModeTab> EQUIPMENT_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
//             SRE.watheId("compact"));
//     ResourceKey<CreativeModeTab> BUILDING_GROUP = EQUIPMENT_GROUP;
//     ResourceKey<CreativeModeTab> DECORATION_GROUP = EQUIPMENT_GROUP;

    Item KEY = registrar.create("key", new KeyItem(new Item.Properties().stacksTo(1)));
    Item LOCKPICK = registrar.create("lockpick", new LockpickItem(new Item.Properties().stacksTo(1)));
    Item KNIFE = registrar.create("knife", new KnifeItem(new Item.Properties().stacksTo(1)));
    Item BAT = registrar.create("bat",
            new BatItem(new Item.Properties().stacksTo(1)
                    .attributes(AxeItem.createAttributes(Tiers.WOOD, 0.0F, -3.0F))));
    Item CROWBAR = registrar.create("crowbar", new CrowbarItem(new Item.Properties().stacksTo(1)));
    Item DEFENSE_VIAL = registrar.create("defense_vial",
            new DefenseItem(new Item.Properties().stacksTo(1)));
    Item WEAK_DEFENSE_VIAL = registrar.create("weak_defense_vial",
            new WeakDefenseItem(new Item.Properties().stacksTo(1)));
    Item GRENADE = registrar.create("grenade", new GrenadeItem(new Item.Properties().stacksTo(1)));
    Item THROWN_GRENADE = registrar.create("thrown_grenade", new GrenadeItem(new Item.Properties().stacksTo(1)));
    Item FIRECRACKER = registrar.create("firecracker", new FirecrackerItem(new Item.Properties().stacksTo(1)));
    Item REVOLVER = registrar.create("revolver", new RevolverItem(new Item.Properties().stacksTo(1)));
    Item STANDARD_REVOLVER = registrar.create("standard_revolver", new StandardRevolverItem(new Item.Properties().stacksTo(1)));
    Item DERRINGER = registrar.create("derringer", new DerringerItem(new Item.Properties().stacksTo(1)));
    Item BODY_BAG = registrar.create("body_bag", new BodyBagItem(new Item.Properties().stacksTo(1)));
    Item LETTER = registrar.create("letter", new Item(new Item.Properties().stacksTo(1)));
    Item BLACKOUT = registrar.create("blackout", new Item(new Item.Properties().stacksTo(1)));
    Item PSYCHO_MODE = registrar.create("psycho_mode", new Item(new Item.Properties().stacksTo(1)));
    Item POISON_VIAL = registrar.create("poison_vial", new Item(new Item.Properties().stacksTo(1)));
    Item SCORPION = registrar.create("scorpion", new Item(new Item.Properties().stacksTo(1)));
    Item OLD_FASHIONED = registrar.create("old_fashioned",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)));
    Item MOJITO = registrar.create("mojito",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)));
    Item MARTINI = registrar.create("martini",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)));
    Item COSMOPOLITAN = registrar.create("cosmopolitan",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)));
    Item CHAMPAGNE = registrar.create("champagne",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)));
    Item NOTE = registrar.create("note", new NoteItem(new Item.Properties().stacksTo(4)));

    static void initialize() {
        registrar.registerEntries();

        // 注册蓄力物品
        ChargeableItemRegistry.register(WatheBridgerItems.KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(WatheBridgerItems.GRENADE, new GrenadeChargeableItem());
    }
}