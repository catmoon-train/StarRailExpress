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
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.TraitorAndModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class THTenshiRole extends TouhouRole {
    public final static ArrayList<ShopEntry> SHOP = new ArrayList<>();
    static {
        var potion = Items.SPLASH_POTION.getDefaultInstance();
        potion.set(DataComponents.POTION_CONTENTS,
                new PotionContents(Optional.empty(), Optional.of(16185078),
                        List.of(ModEffects.of(MobEffects.NIGHT_VISION, 30 * 20, 0, false, false, true))));
        SHOP.add(new ShopEntry(potion,
                200, ShopEntry.Type.TOOL));
    }

    public THTenshiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        addFlag("th_misc");
        setVigilanteTeam(true);
        setSpecialVigilante(true);
    }

    /**
     * 当赋予modifier时调用，如果需要操作modifiers列表可以直接操纵，不需要同步，也不需要调用WorldModifierComponent的sync
     * 
     * @param player
     * @param modifiers
     */
    @Override
    public void onAssignedModifiers(ServerPlayer player, Set<SREModifier> modifiers) {
        // - 自带 Jeb 与隐秘修饰符，且可与其他修饰符共存。
        modifiers.add(TraitorAndModifiers.NIGHT_OWL);
    };

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
