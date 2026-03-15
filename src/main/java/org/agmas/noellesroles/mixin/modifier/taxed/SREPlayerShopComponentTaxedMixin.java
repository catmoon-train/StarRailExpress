package org.agmas.noellesroles.mixin.modifier.taxed;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.modifier.taxed.TaxedModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 为SREPlayerShopComponent添加税收支持
 * 当玩家有taxed修饰符时，减少其金币收入
 */
@Mixin(SREPlayerShopComponent.class)
public class SREPlayerShopComponentTaxedMixin {

    @ModifyVariable(method = "addToBalance(I)V", at = @At("HEAD"), argsOnly = true)
    private int noellesroles$applyTax(int amount) {
        try {
            // 不处理负数(扣款)
            if (amount <= 0) {
                return amount;
            }

            // 获取玩家实例
            SREPlayerShopComponent self = (SREPlayerShopComponent) (Object) this;
            var player = self.getPlayer();

            if (!(player instanceof ServerPlayer sp)) {
                return amount;
            }

            // 检查是否应该征税
            if (!TaxedModifier.shouldApplyTax(sp)) {
                return amount;
            }

            // 应用税收
            return TaxedModifier.applyTax(amount);
        } catch (Throwable t) {
            // 出错时返回原始金额
            return amount;
        }
    }
}
