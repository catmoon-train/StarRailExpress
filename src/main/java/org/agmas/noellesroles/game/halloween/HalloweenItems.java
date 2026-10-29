package org.agmas.noellesroles.game.halloween;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.game.halloween.item.HalloweenItem;
import org.agmas.noellesroles.game.halloween.item.HalloweenItem.Action;
import org.agmas.noellesroles.game.halloween.item.PumpkinCostumeItem;

/** 本模块的唯一注册入口；必须在模组初始化阶段调用，而不是等到世界加载后。 */
public final class HalloweenItems {
    public static final Item PUMPKIN_PRESIDENT = register("pumpkin_president", new HalloweenItem(new Item.Properties().stacksTo(16), Action.PRESIDENT));
    public static final Item BLOOD_SAW = register("blood_saw", new HalloweenItem(new Item.Properties().durability(8), Action.NONE));
    public static final Item HEART = basic("heart", Action.HEART);
    public static final Item CANDY = basic("candy", Action.CANDY);
    public static final Item CANDY_BAG = basic("candy_bag", Action.BAG);
    public static final Item PUMPKIN = basic("pumpkin", Action.NONE);
    public static final Item SOUL_LINK = register("soul_link", new HalloweenItem(new Item.Properties().stacksTo(1), Action.NONE));
    public static final Item BONE_SWORD = register("bone_sword", new HalloweenItem(new Item.Properties().stacksTo(1), Action.NONE));
    public static final Item COIN = basic("coin", Action.COIN);
    public static final Item FRIGHT_GUN = register("fright_gun", new HalloweenItem(new Item.Properties().stacksTo(1), Action.NONE));
    public static final Item SOUL_CONTRACT = basic("soul_contract", Action.NONE);
    public static final Item PUMPKIN_HELMET = register("pumpkin_helmet", new PumpkinCostumeItem(ArmorItem.Type.HELMET));
    public static final Item PUMPKIN_CHESTPLATE = register("pumpkin_chestplate", new PumpkinCostumeItem(ArmorItem.Type.CHESTPLATE));
    public static final Item PUMPKIN_LEGGINGS = register("pumpkin_leggings", new PumpkinCostumeItem(ArmorItem.Type.LEGGINGS));
    public static final Item PUMPKIN_BOOTS = register("pumpkin_boots", new PumpkinCostumeItem(ArmorItem.Type.BOOTS));
    private static boolean initialized;

    private HalloweenItems() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
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

    private static Item basic(String id, Action action) {
        return register(id, new HalloweenItem(new Item.Properties(), action));
    }

    private static Item register(String id, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("noellesroles", "halloween_" + id), item);
    }
}
