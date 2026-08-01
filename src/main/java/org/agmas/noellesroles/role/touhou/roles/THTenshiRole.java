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

package org.agmas.noellesroles.role.touhou.roles;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public class THTenshiRole extends TouhouRole {
    public final static ArrayList<ShopEntry> SHOP = new ArrayList<>();
    static {
        // 监察员的商店

        var displayer = Items.BARRIER.getDefaultInstance();
        displayer.set(DataComponents.ITEM_NAME,
                Component.translatable("gui.noellesroles.tenshi.cooldown_item")
                        .withStyle(ChatFormatting.RED));
        SHOP.add(new ShopEntry(displayer, 0, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                return false;
            }
        });
    }

    public THTenshiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("th_misc");
        setVigilanteTeam(true);
        setSpecialVigilante(true);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(ModItems.SCARLET_PERCEPTION_SWORD.getDefaultInstance());
        return items;
    }

    public List<ShopEntry> getShopEntries() {
        return SHOP;
    }
}
