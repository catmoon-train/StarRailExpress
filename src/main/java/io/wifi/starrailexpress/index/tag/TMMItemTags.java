package io.wifi.starrailexpress.index.tag;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public interface TMMItemTags {

    TagKey<Item> GUNS = create("guns");
    TagKey<Item> BOWS = create("bows");
    TagKey<Item> HELD_LIKE_GUNS_ITEMS = create("held_like_guns");
    TagKey<Item> HELD_LIKE_BAT_ITEMS = create("held_like_bat");
    TagKey<Item> COOLDOWN_GUNS = create("cooldown_guns");
    TagKey<Item> PSYCHOSIS_ITEMS = create("psychosis_items");

    private static TagKey<Item> create(String id) {
        return TagKey.create(Registries.ITEM, SRE.id(id));
    }
}
