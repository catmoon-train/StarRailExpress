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

package org.agmas.noellesroles.init;

import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.index.TMMDescItems;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.Item;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.*;

public class FunnyItems {
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    // 波纹勋章
    public static final Item HOT_POTATO = register(
            new HotPotatoItem(new Item.Properties().stacksTo(1)),
            "hot_potato");
    public static final Item BOWEN_BADGE = register(
            new BowenBadgeItem(new Item.Properties().stacksTo(1)),
            "bowen_badge");
    public static final Item SHISIYE = register(
            new ShisiyeItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)),
            "shisiye");
    public static final Item ICE_RED_TEA = register(
            new IceRedTeaItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)),
            "ice_red_tea");
    public static final Item SUIKA_GOURD = register(
            new SuikaGourdItem(new Item.Properties().stacksTo(1).food(Foods.HONEY_BOTTLE)),
            "suika_gourd");
    public static final Item SUIKA_PILL = register(
            new SuikaPillItem(new Item.Properties().stacksTo(1).food(Foods.BEEF)),
            "suika_pill");
    public static final Item PROBLEM_SET = register(
            new ProblemSetItem(new Item.Properties().stacksTo(1)),
            "problem_set");
    public static final Item DOREMY_GHOST = register(
            new DoremyGhostItem(new Item.Properties().stacksTo(1).food(Foods.APPLE)),
            "doremy_ghost");
    public static final Item COOKED_HAIMAN = register(
            new CookedHaimanItem(new Item.Properties().stacksTo(1).food(Foods.TROPICAL_FISH)),
            "cooked_haiman");
    // 彩虹马蹄铁 - 召唤海曼彩虹马从天而降
    public static final Item RAINBOW_HORSESHOE = register(
            new RainbowHorseshoeItem(new Item.Properties().stacksTo(1)),
            "rainbow_horseshoe");
    // 残月萨马蹄铁 - 召唤残月萨马从天而降
    public static final Item CANYUESA_HORSESHOE = register(
            new CanyuesaHorseshoeItem(new Item.Properties().stacksTo(1)),
            "canyuesa_horseshoe");
    // 超级猪马蹄铁 - 召唤超级猪马从天而降
    public static final Item SUPER_PIG_HORSESHOE = register(
            new SuperPigHorseshoeItem(new Item.Properties().stacksTo(1)),
            "super_pig_horseshoe");

    @SuppressWarnings("unchecked")
    public static Item register(Item item, String id) {
        var registeredItem = registrar.create(id, item,
                new ResourceKey[] { TMMItems.FUNNY_ITEMS_GROUP, TMMItems.NOELLESROLES_ALL_GROUP });
        TMMDescItems.introItems.add(registeredItem);
        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();
    }

}