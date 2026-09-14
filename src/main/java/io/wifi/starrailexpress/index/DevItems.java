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
import net.minecraft.world.item.Rarity;

// OP以及建造使用的物品
public class DevItems {
    public static final ItemRegistrar registrar = new ItemRegistrar(SRE.MOD_ID);

    public static Item OPEN_LIGHT_TOOL = register(new OpenLightToolItem(new Item.Properties().stacksTo(1)),
            "open_light_tool");
    public static Item BINDING_TOOL = register(new BindingToolItem(new Item.Properties().stacksTo(1)),
            "binding_tool");
    public static Item LOOPING_MIRROR_TOOL = register(
            new LoopingMirrorToolItem(new Item.Properties().stacksTo(1)),
            "looping_mirror_tool");
    public static Item VERTICAL_LOOPING_MIRROR_TOOL = register(
            new VerticalLoopingMirrorToolItem(new Item.Properties().stacksTo(1)),
            "vertical_looping_mirror_tool");
    public static Item MAP_TOOL = register(new MapBuildHelperItem(new Item.Properties().stacksTo(1)),
            "map_tool");

    public static Item BREAKING_BRIDGE_TOOL = register(
            new BreakingBridgeToolItem(new Item.Properties().stacksTo(1),ModSceneBlocks.BREAKING_BRIDGE),
            "breaking_bridge_tool");
    public static Item FAKE_BLOCK_TOOL = register(new BreakingBridgeToolItem(new Item.Properties().stacksTo(1),ModSceneBlocks.FAKE_BLOCK),
            "fake_block_tool");
    public static Item CUSTOM_ROLE_TOOL = register(new CustomRoleToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            "custom_role_tool");
    public static Item CUSTOM_MODIFIER_TOOL = register(
            new io.wifi.starrailexpress.custommodifier.CustomModifierToolItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            "custom_modifier_tool");
    /**
     * 自定义列车物品：全模组只有这一个物品，所有自定义列车物品都是它 + 物品数据，
     * 默认没有材质（模型引用到不存在的地方）。
     */
    public static Item CUSTOM_ITEM = register(
            new io.wifi.starrailexpress.customitem.CustomItem(new Item.Properties().stacksTo(1)),
            "custom_item");
    /** 自定义列车物品工具（材质继承原版木棍）。 */
    public static Item CUSTOM_ITEM_TOOL = register(
            new io.wifi.starrailexpress.customitem.CustomItemToolItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            "custom_item_tool");
    /** 自定义方块工具（材质继承原版木棍）。方块本体与其物品在 SREBlocks 里注册。 */
    public static Item CUSTOM_BLOCK_TOOL = register(
            new io.wifi.starrailexpress.customblock.CustomBlockToolItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)),
            "custom_block_tool");

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
        // 自定义列车物品的行为引擎（事件注册幂等）
        io.wifi.starrailexpress.customitem.CustomItemRuntime.init();
        // 自定义方块的交互事件引擎（事件注册幂等）
        io.wifi.starrailexpress.customblock.CustomBlockRuntime.init();
    }
}
