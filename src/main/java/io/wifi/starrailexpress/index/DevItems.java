package io.wifi.starrailexpress.index;


import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.BindingToolItem;
import io.wifi.starrailexpress.content.item.map_dev.MapBuildHelperItem;
import io.wifi.starrailexpress.customrole.CustomRoleToolItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

// OP以及建造使用的物品
public class DevItems {
    public static final ItemRegistrar registrar = new ItemRegistrar(SRE.MOD_ID);
    public static Item BINDING_TOOL = register(new BindingToolItem(new Item.Properties().stacksTo(1)),
            "binding_tool");
    public static Item MAP_TOOL = register(new MapBuildHelperItem(new Item.Properties().stacksTo(1)),
            "map_tool");
    public static Item CUSTOM_ROLE_TOOL = register(new CustomRoleToolItem(new Item.Properties().stacksTo(1)),
            "custom_role_tool");
    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        // Create the identifier for the item.
        // Register the item.
        var registeredItem = registrar.create(id, item, new ResourceKey[] { CreativeModeTabs.OP_BLOCKS });

        // Return the registered item!
        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();
    }
}
