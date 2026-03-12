package io.wifi.starrailexpress.index.wathe_bridge;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.impl.GrenadeChargeableItem;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import io.wifi.starrailexpress.item.*;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;

@SuppressWarnings("unchecked")
public interface WatheBridgerItems {
    public static ItemRegistrar registrar = new ItemRegistrar(SRE.WATHE_MOD_ID);

    ResourceKey<CreativeModeTab> EQUIPMENT_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.watheId("compact"));
    ResourceKey<CreativeModeTab> BUILDING_GROUP = EQUIPMENT_GROUP;
    ResourceKey<CreativeModeTab> DECORATION_GROUP = EQUIPMENT_GROUP;

    Item KEY = registrar.create("key", new KeyItem(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item IRON_DOOR_KEY = registrar.create("iron_door_key",
            new IronDoorKeyItem(new Item.Properties().stacksTo(1).durability(3)), EQUIPMENT_GROUP);
    Item LOCKPICK = registrar.create("lockpick", new LockpickItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item KNIFE = registrar.create("knife", new KnifeItem(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item BAT = registrar.create("bat",
            new BatItem(new Item.Properties().stacksTo(1)
                    .attributes(AxeItem.createAttributes(Tiers.WOOD, 0.0F, -3.0F))),
            EQUIPMENT_GROUP);
    Item CROWBAR = registrar.create("crowbar", new CrowbarItem(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item DEFENSE_VIAL = registrar.create("defense_vial",
            new DefenseItem(new Item.Properties().stacksTo(1)));
    Item GRENADE = registrar.create("grenade", new GrenadeItem(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item THROWN_GRENADE = registrar.create("thrown_grenade", new GrenadeItem(new Item.Properties().stacksTo(1)));
    // Item HandCuffsItem = registrar.create("hand_cuffs", new HandCuffsItem(new
    // Item.Settings().maxCount(1)));
    Item FIRECRACKER = registrar.create("firecracker", new FirecrackerItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item REVOLVER = registrar.create("revolver", new RevolverItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item DERRINGER = registrar.create("derringer", new DerringerItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item BODY_BAG = registrar.create("body_bag", new BodyBagItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item LETTER = registrar.create("letter", new Item(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item BLACKOUT = registrar.create("blackout", new Item(new Item.Properties().stacksTo(1)));
    Item PSYCHO_MODE = registrar.create("psycho_mode", new Item(new Item.Properties().stacksTo(1)));
    Item POISON_VIAL = registrar.create("poison_vial", new Item(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item SCORPION = registrar.create("scorpion", new Item(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item OLD_FASHIONED = registrar.create("old_fashioned",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), EQUIPMENT_GROUP);
    Item MOJITO = registrar.create("mojito",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), EQUIPMENT_GROUP);
    Item MARTINI = registrar.create("martini",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), EQUIPMENT_GROUP);
    Item COSMOPOLITAN = registrar.create("cosmopolitan",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), EQUIPMENT_GROUP);
    Item CHAMPAGNE = registrar.create("champagne",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), EQUIPMENT_GROUP);
    Item NOTE = registrar.create("note", new NoteItem(new Item.Properties().stacksTo(4)), EQUIPMENT_GROUP);

    static void initialize() {
        registrar.registerEntries();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BUILDING_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.compact"))
                .icon(() -> new ItemStack(WatheBridgerBlocks.TARNISHED_GOLD_PILLAR))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DECORATION_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.decoration"))
                .icon(() -> new ItemStack(WatheBridgerBlocks.TARNISHED_GOLD_VENT_SHAFT))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EQUIPMENT_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.equipment"))
                .icon(() -> new ItemStack(KEY))
                .build());

        // 注册蓄力物品
        ChargeableItemRegistry.register(WatheBridgerItems.KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(WatheBridgerItems.GRENADE, new GrenadeChargeableItem());
    }
}