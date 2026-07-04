package cn.zbx1425.minopp.platform;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import java.util.function.Function;
import java.util.function.Supplier;

public class GroupedItem extends Item {

    public final Supplier<ResourceKey<CreativeModeTab>> tabSupplier;

    public GroupedItem(
            Supplier<ResourceKey<CreativeModeTab>> tabSupplier,
            Function<Properties, Properties> properties) {
        super(properties.apply(new Properties()));
        this.tabSupplier = tabSupplier;
    }
}

