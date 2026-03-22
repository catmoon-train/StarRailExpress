package org.agmas.noellesroles.repack;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.index.TMMDescItems;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.repack.items.*;

public class HSRItems {
    public static final String PILL_POISONOUS_KEY = "poisonous";

    public static ResourceKey<CreativeModeTab> HSR_CREATIVE_GROUP = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
            Noellesroles.id("hsritems"));
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    public static final Item ANTIDOTE = register(new AntidoteItem((new Item.Properties()).stacksTo(1)), "antidote");
    public static final Item PILL = register(new PillItem((new Item.Properties()).stacksTo(16).food((new FoodProperties.Builder()).nutrition(1).saturationModifier(0.1F).alwaysEdible().build())), "pill");
    public static final Item TOXIN = register(new ToxinItem((new Item.Properties()).stacksTo(1)), "toxin");
    public static final Item CATALYST = register(new CatalystItem((new Item.Properties()).stacksTo(1)), "catalyst");
    // public static final Item MASTER_KEY = register(new MasterKeyItem((new
    // Item.Settings()).maxCount(1).maxDamage(3)), "master_key");
    public static final Item BANDIT_REVOLVER = register(new BanditRevolverItem((new Item.Properties()).stacksTo(1)),
            "bandit_revolver");

    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        var registeredItem = registrar.create(id, item, new ResourceKey[] { HSR_CREATIVE_GROUP });
        TMMDescItems.introItems.add(registeredItem);
        return registeredItem;
    }

    public static ItemStack createPillStack(boolean poisonous) {
        ItemStack stack = PILL.getDefaultInstance();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PILL_POISONOUS_KEY, poisonous);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static void init() {
        registrar.registerEntries();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HSR_CREATIVE_GROUP, FabricItemGroup.builder()
                .title(Component.translatable("item_group.noellesroles.hsritems")).icon(() -> {
                    return new ItemStack(HSRItems.BANDIT_REVOLVER);
                }).build());
    }
}
