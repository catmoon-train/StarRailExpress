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

package org.agmas.noellesroles.content.item.angler;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerItemTags;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 负数耐久钓竿：永不损坏，任何人捡到都能对着水钓。
 */
public class ErrorAnglerRodItem extends AnglerRodItem {
    public ErrorAnglerRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        return org.agmas.noellesroles.game.roles.innocence.angler.AnglerCatchHandler.createErrorRod();
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot,
            boolean selected) {
        if (!level.isClientSide && entity instanceof Player) {
            AnglerWorldMemory.markErrorRodClaimed();
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        int uses = AnglerItemTags.errorUses(stack);
        String shown = (System.currentTimeMillis() / 200L) % 2 == 0 ? String.valueOf(uses) : "??";
        tooltip.add(Component.translatable("item.noellesroles.error_angler_rod.durability", shown)
                .withStyle(ChatFormatting.DARK_RED));
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return 13;
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0x6B0000;
    }
}
