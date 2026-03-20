package io.wifi.starrailexpress.index;

import java.util.ArrayList;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.impl.GrenadeChargeableItem;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import io.wifi.starrailexpress.index.wathe_bridge.WatheBridgerItems;
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
public interface TMMItems {
    public static ItemRegistrar registrar = new ItemRegistrar(SRE.TMM_MOD_ID);
    public static ArrayList<Item> INVISIBLE_ITEMS = new ArrayList<>();
    public ReplaceableItems INIT_ITEMS = new ReplaceableItems();

    /**
     * 可换皮肤的物品
     */
    public ArrayList<Item> SkinableItem = new ArrayList<>();

    ResourceKey<CreativeModeTab> BUILDING_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("building"));
    ResourceKey<CreativeModeTab> DECORATION_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("decoration"));
    ResourceKey<CreativeModeTab> EQUIPMENT_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("equipment"));

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
            new DefenseItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item GRENADE = registrar.create("grenade", new GrenadeItem(new Item.Properties().stacksTo(1)), EQUIPMENT_GROUP);
    Item FIRECRACKER = registrar.create("firecracker", new FirecrackerItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item REVOLVER = registrar.create("revolver", new RevolverItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item DERRINGER = registrar.create("derringer", new DerringerItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item SNIPER_RIFLE = registrar.create("sniper_rifle", new SniperRifleItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item MAGNUM_BULLET = registrar.create("magnum_bullet", new MagnumBulletItem(new Item.Properties().stacksTo(64)),
            EQUIPMENT_GROUP);
    Item SCOPE = registrar.create("scope", new ScopeItem(new Item.Properties().stacksTo(1)),
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
    Item BINDING_TOOL = registrar.create("binding_tool", new BindingToolItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);
    Item NUNCHUCK = registrar.create("nunchuck", new NunchuckItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP);

    static void initialize() {
        INVISIBLE_ITEMS.add(TMMItems.NOTE);
        INVISIBLE_ITEMS.add(TMMItems.DEFENSE_VIAL);

        DefenseItem.canUseByRightClickRolePaths.add(TMMRoles.LOOSE_END.identifier().getPath());

        registrar.registerEntries();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BUILDING_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.building"))
                .icon(() -> new ItemStack(TMMBlocks.TARNISHED_GOLD_PILLAR))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DECORATION_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.decoration"))
                .icon(() -> new ItemStack(TMMBlocks.TARNISHED_GOLD_VENT_SHAFT))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EQUIPMENT_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.equipment"))
                .icon(() -> new ItemStack(KEY))
                .build());
        if (INIT_ITEMS.LETTER == null)
            INIT_ITEMS.LETTER = LETTER;

        SkinableItem.add(TMMItems.KNIFE);
        SkinableItem.add(TMMItems.REVOLVER);
        // SkinnableItem.add(TMMItems.LOCKPICK);
        SkinableItem.add(TMMItems.GRENADE);
        SkinableItem.add(TMMItems.BAT);

        // 注册蓄力物品
        ChargeableItemRegistry.register(TMMItems.KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(TMMItems.GRENADE, new GrenadeChargeableItem());
        WatheBridgerItems.initialize();
    }
}