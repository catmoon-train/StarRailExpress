package io.wifi.starrailexpress.index;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.impl.GrenadeChargeableItem;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import io.wifi.starrailexpress.content.item.*;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.wathe_bridge.WatheBridgerItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.*;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;

@SuppressWarnings("unchecked")
public interface TMMItems {
    public static ItemRegistrar registrar = new ItemRegistrar(SRE.TMM_MOD_ID);
    public static ItemRegistrar sreRegistrar = new ItemRegistrar(SRE.MOD_ID);
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

    // === 新功能分类标签页 ===
    ResourceKey<CreativeModeTab> WEAPONS_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("weapons"));
    ResourceKey<CreativeModeTab> TOOLS_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("tools"));
    ResourceKey<CreativeModeTab> CONSUMABLES_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("consumables"));
    ResourceKey<CreativeModeTab> SANITY_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("sanity"));
    ResourceKey<CreativeModeTab> ROLE_ITEMS_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("role_items"));
    ResourceKey<CreativeModeTab> MISC_ITEMS_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("misc_items"));

    // === 特殊分类/汇总标签页 ===
    ResourceKey<CreativeModeTab> REPAIR_MODE_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("repair_mode"));
    ResourceKey<CreativeModeTab> SEALED_ARTIFACTS_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("sealed_artifacts"));
    ResourceKey<CreativeModeTab> SRE_ALL_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            SRE.id("all"));
    ResourceKey<CreativeModeTab> NOELLESROLES_ALL_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("all"));

    // === 武器 (WEAPONS) ===
    Item KNIFE = registrar.create("knife", new KnifeItem(new Item.Properties().stacksTo(1)), WEAPONS_GROUP, SRE_ALL_GROUP);
    Item BAT = registrar.create("bat",
            new BatItem(new Item.Properties().stacksTo(1)
                    .attributes(AxeItem.createAttributes(Tiers.WOOD, 0.0F, -3.0F))),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item CROWBAR = registrar.create("crowbar", new CrowbarItem(new Item.Properties().stacksTo(1)), WEAPONS_GROUP, SRE_ALL_GROUP);
    Item GRENADE = registrar.create("grenade", new GrenadeItem(new Item.Properties().stacksTo(1)), WEAPONS_GROUP, SRE_ALL_GROUP);
    Item STICKY_GRENADE = registrar.create("sticky_grenade", new StickyGrenadeItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item TIMED_GRENADE = registrar.create("timed_grenade", new TimedGrenadeItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item FIRECRACKER = registrar.create("firecracker", new FirecrackerItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item REVOLVER = registrar.create("revolver", new RevolverItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item STANDARD_REVOLVER = registrar.create("standard_revolver", new StandardRevolverItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item DERRINGER = registrar.create("derringer", new DerringerItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item SNIPER_RIFLE = registrar.create("sniper_rifle", new SniperRifleItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);
    Item NUNCHUCK = registrar.create("nunchuck", new NunchuckItem(new Item.Properties().stacksTo(1)),
            WEAPONS_GROUP, SRE_ALL_GROUP);

    // === 工具 (TOOLS) ===
    Item KEY = registrar.create("key", new KeyItem(new Item.Properties().stacksTo(1)), TOOLS_GROUP, SRE_ALL_GROUP);
    Item IRON_DOOR_KEY = registrar.create("iron_door_key",
            new IronDoorKeyItem(new Item.Properties().stacksTo(1).durability(3)), TOOLS_GROUP, SRE_ALL_GROUP);
    Item LOCKPICK = registrar.create("lockpick", new LockpickItem(new Item.Properties().stacksTo(1)),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item DEFENSE_VIAL = registrar.create("defense_vial",
            new DefenseItem(new Item.Properties().stacksTo(1)),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item DISGUISE_1 = registrar.create("disguise_1",
            new DisguiseItem(new Item.Properties().stacksTo(16), 0),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item DISGUISE_2 = registrar.create("disguise_2",
            new DisguiseItem(new Item.Properties().stacksTo(16), 1),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item DISGUISE_3 = registrar.create("disguise_3",
            new DisguiseItem(new Item.Properties().stacksTo(16), 2),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item SCOPE = registrar.create("scope", new ScopeItem(new Item.Properties().stacksTo(1)),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item BODY_BAG = registrar.create("body_bag", new BodyBagItem(new Item.Properties().stacksTo(1)),
            TOOLS_GROUP, SRE_ALL_GROUP);
    Item LETTER = registrar.create("letter", new Item(new Item.Properties().stacksTo(1)), TOOLS_GROUP, SRE_ALL_GROUP);
    Item NOTE = registrar.create("note", new NoteItem(new Item.Properties().stacksTo(4)), TOOLS_GROUP, SRE_ALL_GROUP);

    // === 消耗品 (CONSUMABLES) ===
    Item MAGNUM_BULLET = registrar.create("magnum_bullet", new MagnumBulletItem(new Item.Properties().stacksTo(64)),
            CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item POISON_VIAL = registrar.create("poison_vial", new Item(new Item.Properties().stacksTo(1)),
            CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item SCORPION = registrar.create("scorpion", new Item(new Item.Properties().stacksTo(1)), CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item OLD_FASHIONED = registrar.create("old_fashioned",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item MOJITO = registrar.create("mojito",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item MARTINI = registrar.create("martini",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item COSMOPOLITAN = registrar.create("cosmopolitan",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), CONSUMABLES_GROUP, SRE_ALL_GROUP);
    Item CHAMPAGNE = registrar.create("champagne",
            new CocktailItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)), CONSUMABLES_GROUP, SRE_ALL_GROUP);

    // === 装备 (EQUIPMENT) ===
    Item EMOJI_HELMET = registrar.create("emoji_helmet", new EmojiHelmetItem(new Item.Properties().stacksTo(1)),
            EQUIPMENT_GROUP, SRE_ALL_GROUP);
    Item DRAWING_BOARD = sreRegistrar.create("drawing_board", new io.wifi.starrailexpress.content.item.DrawingBoardItem(), EQUIPMENT_GROUP, SRE_ALL_GROUP);
    Item ADMIN_DRAWING_BOARD = sreRegistrar.create("admin_drawing_board", new io.wifi.starrailexpress.content.item.AdminDrawingBoardItem(), new net.minecraft.resources.ResourceKey[]{net.minecraft.world.item.CreativeModeTabs.OP_BLOCKS});

    // === 杂项 (MISC) ===
    Item BLACKOUT = registrar.create("blackout", new Item(new Item.Properties().stacksTo(1)), SRE_ALL_GROUP);
    Item MONITOR_BROKEN = registrar.create("monitor_broken", new Item(new Item.Properties().stacksTo(1)), SRE_ALL_GROUP);
    Item PSYCHO_MODE = registrar.create("psycho_mode", new Item(new Item.Properties().stacksTo(1)), SRE_ALL_GROUP);

    public static void initialize() {
        INVISIBLE_ITEMS.add(TMMItems.NOTE);
        INVISIBLE_ITEMS.add(TMMItems.DEFENSE_VIAL);

        // 亡命徒，超级亡命徒，土块 可以直接使用防御药剂
        DefenseItem.canUseByRightClickRolePaths.add(TMMRoles.LOOSE_END.identifier().getPath());
        DefenseItem.canUseByRightClickRolePaths.add(SpecialGameModeRoles.SUPER_LOOSE_END.identifier().getPath());
        DefenseItem.canUseByRightClickRolePaths.add(SpecialGameModeRoles.DIRT.identifier().getPath());

        registrar.registerEntries();

        // 注册方块类标签页（保持不变）
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BUILDING_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.building"))
                .icon(() -> new ItemStack(TMMBlocks.TARNISHED_GOLD_PILLAR))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, DECORATION_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.decoration"))
                .icon(() -> new ItemStack(TMMBlocks.TARNISHED_GOLD_VENT_SHAFT))
                .build());

        // 注册功能分类标签页
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, WEAPONS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.weapons"))
                .icon(() -> new ItemStack(REVOLVER))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TOOLS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.tools"))
                .icon(() -> new ItemStack(LOCKPICK))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EQUIPMENT_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.equipment"))
                .icon(() -> new ItemStack(EMOJI_HELMET))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CONSUMABLES_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.consumables"))
                .icon(() -> new ItemStack(OLD_FASHIONED))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SANITY_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.sanity"))
                .icon(() -> new ItemStack(OLD_FASHIONED))  // 临时图标，后续被 ModItems 中的 WREATH 覆盖
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ROLE_ITEMS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.role_items"))
                .icon(() -> new ItemStack(OLD_FASHIONED))  // 临时图标
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MISC_ITEMS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.misc_items"))
                .icon(() -> new ItemStack(BLACKOUT))
                .build());

        // 注册特殊分类/汇总标签页
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, REPAIR_MODE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.repair_mode"))
                .icon(() -> new ItemStack(TMMItems.IRON_DOOR_KEY))  // 临时图标
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SEALED_ARTIFACTS_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.sealed_artifacts"))
                .icon(() -> new ItemStack(TMMItems.IRON_DOOR_KEY))  // 临时图标
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, SRE_ALL_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.starrailexpress.all"))
                .icon(() -> new ItemStack(KEY))
                .build());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, NOELLESROLES_ALL_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("itemGroup.noellesroles.all"))
                .icon(() -> new ItemStack(TMMItems.IRON_DOOR_KEY))  // 临时图标
                .build());
        if (INIT_ITEMS.LETTER == null)
            INIT_ITEMS.LETTER = LETTER;

        SkinableItem.add(TMMItems.KNIFE);
        SkinableItem.add(TMMItems.REVOLVER);
        // SkinnableItem.add(TMMItems.LOCKPICK);
        SkinableItem.add(TMMItems.GRENADE);
        SkinableItem.add(TMMItems.STICKY_GRENADE);
        SkinableItem.add(TMMItems.TIMED_GRENADE);
        SkinableItem.add(TMMItems.BAT);

        // 注册蓄力物品
        ChargeableItemRegistry.register(TMMItems.KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(TMMItems.GRENADE, new GrenadeChargeableItem());
        ChargeableItemRegistry.register(TMMItems.STICKY_GRENADE, new GrenadeChargeableItem());
        WatheBridgerItems.initialize();
        sreRegistrar.registerEntries();
    }
}
