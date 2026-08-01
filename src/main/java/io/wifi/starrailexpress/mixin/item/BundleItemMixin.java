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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleItem.class)
public class BundleItemMixin {

    private static final float CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT = 16.0F;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void cancelUseIfFull(Level level, Player player, InteractionHand interactionHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!SRE.isLobby) {
            cir.setReturnValue(InteractionResultHolder.pass(player.getItemInHand(interactionHand)));
            cir.cancel();
        }
    }

    @ModifyConstant(method = "appendHoverText", constant = @Constant(intValue = 64, ordinal = 1))
    private int modifyTooltipMaxWeight(int original) {
        return 64 * 4; // 与新上限一致
    }

    @ModifyReturnValue(method = "getFullnessDisplay", at = @At("RETURN"))
    private static float modifyFullnessDisplay(float original, ItemStack stack) {
        BundleContents contents = stack.getOrDefault(
                DataComponents.BUNDLE_CONTENTS,
                BundleContents.EMPTY);

        return contents.weight().floatValue() / CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT;
    }

    @ModifyExpressionValue(method = "getBarWidth", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;mulAndTruncate(Lorg/apache/commons/lang3/math/Fraction;I)I"))
    private int modifyBarWidthValue(int original, ItemStack stack) {
        BundleContents contents = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        float fullness = contents.weight().floatValue() / CUSTOM_BUNDLE_MAX_WEIGHT_FLOAT;

        return Math.min(1 + (int) (fullness * 12.0F), 13);
    }
}