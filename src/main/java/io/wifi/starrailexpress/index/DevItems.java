/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.index;

import org.agmas.noellesroles.init.ModSceneBlocks;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.content.item.*;
import io.wifi.starrailexpress.content.item.map_dev.MapBuildHelperItem;
import io.wifi.starrailexpress.customrole.CustomRoleToolItem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

// OP以及建造使用的物品
public class DevItems {
    public static final ItemRegistrar registrar = new ItemRegistrar(SRE.MOD_ID);

    public static Item OPEN_LIGHT_TOOL = register(new OpenLightToolItem(new Item.Properties().stacksTo(1)),
            "open_light_tool");
    public static Item BINDING_TOOL = register(new BindingToolItem(new Item.Properties().stacksTo(1)),
            "binding_tool");
    public static Item MAP_TOOL = register(new MapBuildHelperItem(new Item.Properties().stacksTo(1)),
            "map_tool");

    public static Item BREAKING_BRIDGE_TOOL = register(
            new BreakingBridgeToolItem(new Item.Properties().stacksTo(1),ModSceneBlocks.BREAKING_BRIDGE),
            "breaking_bridge_tool");
    public static Item FAKE_BLOCK_TOOL = register(new BreakingBridgeToolItem(new Item.Properties().stacksTo(1),ModSceneBlocks.FAKE_BLOCK),
            "fake_block_tool");
    public static Item CUSTOM_ROLE_TOOL = register(new CustomRoleToolItem(new Item.Properties().stacksTo(1)),
            "custom_role_tool");

    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        // Create the identifier for the item.
        // Register the item.
        var registeredItem = registrar.create(id, item, new ResourceKey[] { CreativeModeTabs.OP_BLOCKS });
        TMMDescItems.introItems.add(registeredItem);

        // Return the registered item!
        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();
    }
}
