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
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BundleContents.Mutable.class)
public class BundleContentsMixin {
    @Unique
    private static final Fraction CUSTOM_BUNDLE_MAX_WEIGHT = Fraction.getFraction(4, 1);
    // 1 = 原版 64
    // 2 = 128
    // 4 = 256
    // 8 = 512

    @ModifyExpressionValue(method = "getMaxAmountToAdd", at = @At(value = "FIELD", target = "Lorg/apache/commons/lang3/math/Fraction;ONE:Lorg/apache/commons/lang3/math/Fraction;"))
    private Fraction modifyBundleMaxWeight(Fraction original) {
        return CUSTOM_BUNDLE_MAX_WEIGHT;
    }
}
