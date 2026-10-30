package org.agmas.noellesroles.game.halloween;

import io.wifi.starrailexpress.index.TMMDescItems;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.game.halloween.item.HalloweenItem;
import org.agmas.noellesroles.game.halloween.item.HalloweenItem.Action;
import org.agmas.noellesroles.game.halloween.item.PumpkinCostumeItem;

import java.util.List;

/** 本模块的唯一注册入口；必须在模组初始化阶段调用，而不是等到世界加载后。 */
public final class HalloweenItems {
    public static final Item PUMPKIN_PRESIDENT = register("pumpkin_president", new HalloweenItem(new Item.Properties().stacksTo(16), Action.PRESIDENT));
    public static final Item BLOOD_SAW = register("blood_saw", new HalloweenItem(new Item.Properties().durability(8), Action.NONE));
    public static final Item HEART = basic("heart", Action.HEART);
    public static final Item CANDY = basic("candy", Action.CANDY);
    public static final Item CANDY_BAG = basic("candy_bag", Action.BAG);
    public static final Item PUMPKIN = basic("pumpkin", Action.NONE);
    public static final Item SOUL_LINK = register("soul_link", new HalloweenItem(new Item.Properties().stacksTo(1), Action.NONE));
    public static final Item BONE_SWORD = register("bone_sword", new HalloweenItem(new Item.Properties().durability(HalloweenRewardRules.BONE_DURABILITY), Action.NONE));
    public static final Item COIN = basic("coin", Action.COIN);
    public static final Item FRIGHT_GUN = register("fright_gun", new HalloweenItem(new Item.Properties().stacksTo(1), Action.NONE));
    public static final Item SOUL_CONTRACT = basic("soul_contract", Action.NONE);
    public static final Item PUMPKIN_HELMET = register("pumpkin_helmet", new PumpkinCostumeItem(ArmorItem.Type.HELMET));
    public static final Item PUMPKIN_CHESTPLATE = register("pumpkin_chestplate", new PumpkinCostumeItem(ArmorItem.Type.CHESTPLATE));
    public static final Item PUMPKIN_LEGGINGS = register("pumpkin_leggings", new PumpkinCostumeItem(ArmorItem.Type.LEGGINGS));
    public static final Item PUMPKIN_BOOTS = register("pumpkin_boots", new PumpkinCostumeItem(ArmorItem.Type.BOOTS));
    private static final List<Item> ALL_ITEMS = List.of(PUMPKIN_PRESIDENT, BLOOD_SAW, HEART, CANDY,
            CANDY_BAG, PUMPKIN, SOUL_LINK, BONE_SWORD, COIN, FRIGHT_GUN, SOUL_CONTRACT,
            PUMPKIN_HELMET, PUMPKIN_CHESTPLATE, PUMPKIN_LEGGINGS, PUMPKIN_BOOTS);
    private static boolean initialized;

    private HalloweenItems() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        // 本模块直接注册 Item，不经过 ModItems.register；必须补齐创造栏与介绍页的独立索引。
        TMMDescItems.introItems.addAll(ALL_ITEMS);
        addToGroup(TMMItems.NOELLESROLES_ALL_GROUP, ALL_ITEMS);
        addToGroup(TMMItems.WEAPONS_GROUP, List.of(BLOOD_SAW, BONE_SWORD, FRIGHT_GUN));
        addToGroup(TMMItems.EQUIPMENT_GROUP, List.of(PUMPKIN_HELMET, PUMPKIN_CHESTPLATE,
                PUMPKIN_LEGGINGS, PUMPKIN_BOOTS));
        addToGroup(TMMItems.CONSUMABLES_GROUP, List.of(HEART, CANDY, CANDY_BAG, COIN));
        addToGroup(TMMItems.TOOLS_GROUP, List.of(PUMPKIN_PRESIDENT, SOUL_LINK));
        addToGroup(TMMItems.MISC_ITEMS_GROUP, List.of(PUMPKIN, SOUL_CONTRACT));
        PumpkinHideouts.initialize();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(amount > 0 && entity instanceof Player player && HalloweenConsumables.tryBlockDamage(player)));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Player player) {
                HalloweenConsumables.removePlayer(player);
                PumpkinHideouts.removePlayer(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            HalloweenConsumables.removePlayer(handler.player);
            PumpkinHideouts.removePlayer(handler.player);
        });
    }

    /** 初始化阶段固定登记，不按地图活动开关或每次打开创造栏重复注册。 */
    private static void addToGroup(ResourceKey<CreativeModeTab> group, List<Item> items) {
        ItemGroupEvents.modifyEntriesEvent(group).register(entries -> items.forEach(entries::accept));
    }

    private static Item basic(String id, Action action) {
        return register(id, new HalloweenItem(new Item.Properties(), action));
    }

    private static Item register(String id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_" + id), item);
    }
}
