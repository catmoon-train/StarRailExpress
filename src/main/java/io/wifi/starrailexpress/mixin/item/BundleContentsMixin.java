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
