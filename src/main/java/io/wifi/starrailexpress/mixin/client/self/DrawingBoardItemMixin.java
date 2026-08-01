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

package io.wifi.starrailexpress.mixin.client.self;

import io.wifi.starrailexpress.client.gui.screen.AdminDrawingBoardScreen;
import io.wifi.starrailexpress.client.gui.screen.DrawingBoardScreen;
import io.wifi.starrailexpress.content.item.AdminDrawingBoardItem;
import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(DrawingBoardItem.class)
public class DrawingBoardItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(Level world, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide) {
            ItemStack stack = player.getItemInHand(hand);
            Minecraft.getInstance().execute(() -> {
                if (stack.getItem() instanceof AdminDrawingBoardItem) {
                    Minecraft.getInstance().setScreen(new AdminDrawingBoardScreen(stack));
                } else {
                    Minecraft.getInstance().setScreen(new DrawingBoardScreen(stack));
                }
            });
        }
    }
}
