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

package org.agmas.noellesroles.mixin.roles.angler;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.content.item.angler.AnglerRodItem;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerCatchHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingHookAnglerMixin {

    @Shadow
    private int nibble;

    /**
     * 原版浮漂只认 {@code Items.FISHING_ROD}，自定义钓竿会在下一刻被直接 discard。
     */
    @WrapOperation(method = "shouldStopFishing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean noellesroles$allowCustomFishingRods(ItemStack stack, Item item, Operation<Boolean> original) {
        if (item == Items.FISHING_ROD && stack.getItem() instanceof FishingRodItem) {
            return true;
        }
        return original.call(stack, item);
    }

    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void noellesroles$replaceAnglerLoot(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (!(stack.getItem() instanceof AnglerRodItem)) {
            return;
        }
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        if (this.nibble > 0) {
            Player owner = self.getPlayerOwner();
            if (owner instanceof ServerPlayer serverPlayer) {
                AnglerCatchHandler.handleRetrieve(serverPlayer, stack, self);
            }
        }
        self.discard();
        // 耐久由职业自己扣，避免原版 hurtAndBreak 把竿直接打碎
        cir.setReturnValue(0);
    }
}
