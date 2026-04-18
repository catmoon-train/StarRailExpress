package org.agmas.noellesroles.content.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.agmas.noellesroles.ModDataComponentTypes;

import io.wifi.starrailexpress.content.item.CocktailItem;

public class ChefWaterItem extends CocktailItem {
    private static Properties __warp_init(Properties properties) {
        var tag = new CompoundTag();
        var listTag = new ListTag();
        tag.put("effects", listTag);
        properties.component(ModDataComponentTypes.COOKED, tag);
        return properties;
    }

    public ChefWaterItem(Properties properties) {
        super(__warp_init(properties));
    }

}
