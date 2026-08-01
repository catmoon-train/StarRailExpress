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

package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.progression.ProgressionDataManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class UseItemProgressionMixin {

    @Inject(method = "use", at = @At("RETURN"))
    public void sre$trackUseItemProgression(Level level, Player player, InteractionHand usedHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || cir.getReturnValue() == null
                || !cir.getReturnValue().getResult().consumesAction()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(player.getItemInHand(usedHand).getItem()).toString();
        ProgressionDataManager.onItemUsed(serverPlayer, itemId);
    }
}
