package org.agmas.noellesroles.repack;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.repack.items.AntidoteItem;
import org.agmas.noellesroles.repack.items.BanditRevolverItem;
import org.agmas.noellesroles.repack.items.CatalystItem;
import org.agmas.noellesroles.repack.items.ToxinItem;

public class HSRItems {
    public static ResourceKey<CreativeModeTab> HSR_CREATIVE_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("hsritems"));
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    public static final Item ANTIDOTE = register(new AntidoteItem((new Item.Properties()).stacksTo(1)), "antidote");
    public static final Item TOXIN = register(new ToxinItem((new Item.Properties()).stacksTo(1)), "toxin");
    public static final Item CATALYST = register(new CatalystItem((new Item.Properties()).stacksTo(1)), "catalyst");
    // public static final Item MASTER_KEY = register(new MasterKeyItem((new
    // Item.Settings()).maxCount(1).maxDamage(3)), "master_key");
    public static final Item BANDIT_REVOLVER = register(new BanditRevolverItem((new Item.Properties()).stacksTo(1)),
            "bandit_revolver");

    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        var registeredItem = registrar.create(id, item, new ResourceKey[] { HSR_CREATIVE_GROUP });
        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HSR_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.hsritems")).icon(() -> {
                    return new ItemStack(HSRItems.BANDIT_REVOLVER);
                }).build());
    }
}
