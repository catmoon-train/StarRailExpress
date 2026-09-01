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

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.api.RoleSkill.RoleSkillContext;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class THMamizouRole extends TouhouRole {

    public THMamizouRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        SHOP.addAll(ShopContent.getDefaultKnifeEntries());
        SHOP.addAll(getTargetShopEntries(player));
        return SHOP;
    }

    public List<ShopEntry> getTargetShopEntries(Player player) {
        if (player == null)
            return List.of();
        final var cca = SREAbilityPlayerComponent.KEY.get(player);
        final var rolecca = SRERoleWorldComponent.KEY.get(player.level());
        if (cca.targetUUID != null) {
            final var targetRole = rolecca.getRole(cca.targetUUID);
            List<ShopEntry> shops = new ArrayList<>(ShopContent.getShopEntries(targetRole));
            // 必须是100%的ShopEntry.class类，不能是extends，也不能是内联override，避免bug。
            shops.removeIf((t) -> {
                return t.getClass() != ShopEntry.class && t.getClass() != DiscountShopEntry.class;
            });
            List<ShopEntry> newShops = new ArrayList<>();
            for (final var s : shops) {
                if (s instanceof DiscountShopEntry ks) {
                    newShops.add(new DiscountShopEntry(ks.stack(), (int) (ks.price() * 1.5f), ks.discount()));
                } else {
                    newShops.add(new ShopEntry(s.stack(), (int) (s.price() * 1.5f), s.type()));
                }
            }
            return newShops;
        }
        return List.of();
    }

    public static boolean handleSelect(RoleSkillContext context) {
        final var player = context.player();
        if (context.target() == null) {
            player.displayClientMessage(
                    Component.translatable("skill.noellesroles.mamizou_select.no_target").withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        final var target = context.getTargetAsPlayer();
        SREAbilityPlayerComponent.KEY.get(player).setTarget(target);
        player.displayClientMessage(
                Component.translatable("skill.noellesroles.mamizou_select.success", target.getName())
                        .withStyle(ChatFormatting.GREEN),
                true);
        return true;
    }
}
