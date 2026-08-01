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

package org.agmas.noellesroles.mixin.modifier.taxed;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modifier.taxed.TaxedModifier;
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
