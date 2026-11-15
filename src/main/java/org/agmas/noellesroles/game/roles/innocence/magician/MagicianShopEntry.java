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

package org.agmas.noellesroles.game.roles.innocence.magician;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.game.KillerKnifeShopEntry;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.SteppedPriceShopEntry;
import org.jetbrains.annotations.NotNull;

/**
 * 魔术师伪装商店的条目外壳。
 *
 * <p>它做两件事：</p>
 * <ol>
 * <li><b>加价 20%</b>：复制被伪装职业的条目，但展示价 / 结算价按
 * {@link MagicianShop#PRICE_MULTIPLIER} 上浮（{@code DynamicShopComponent} 的折扣、阶梯价仍在
 * 上浮后的价格上继续生效）。</li>
 * <li><b>特殊商店防护</b>：伪装来的商店可能依赖魔术师身上根本不存在的职业数据
 * （例如影杀者的能量兑换、观者的姿态、处刑者的目标）。这些条目在魔术师身上：
 * <ul>
 * <li>显示 / 可购判定抛异常 → 视为不可显示 / 可购买，绝不让异常冒到商店与网络层；</li>
 * <li>购买执行抛异常 → 直接静默吞掉（购买流程照常结束，不产生任何效果、不崩溃）；</li>
 * <li>购买执行正常返回 {@code false} 的「纯发物品」条目仍按原语义失败（不扣钱、不吞物品）。</li>
 * </ul>
 * </li>
 * </ol>
 */
public class MagicianShopEntry extends ShopEntry {

    private final ShopEntry delegate;

    public MagicianShopEntry(@NotNull ShopEntry delegate) {
        super(copyStack(delegate), markedUpPrice(delegate.price()), delegate.type(),
                delegate.currency(), delegate.weight());
        this.delegate = delegate;
        if (delegate.getFailedMessage() != null) {
            setFailedMessage(delegate.getFailedMessage());
        }
    }

    /** 商店里实际被包装的原始条目（调试 / 测试用）。 */
    public ShopEntry delegate() {
        return this.delegate;
    }

    private static ItemStack copyStack(@NotNull ShopEntry entry) {
        ItemStack stack = entry.stack();
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    /** 加价 20%：向上取整，避免出现「加价后反而便宜」的情况。 */
    private static int markedUpPrice(int basePrice) {
        return Math.max(0, (int) Math.ceil(basePrice * MagicianShop.PRICE_MULTIPLIER));
    }

    @Override
    public boolean canDisplay(@NotNull Player player) {
        try {
            return this.delegate.canDisplay(player);
        } catch (Throwable t) {
            // 伪装职业的显示条件读不到数据（如姿态 / 目标）：对魔术师直接隐藏该条目
            SRE.LOGGER.debug("Magician shop entry hidden: {}", this.delegate.stack(), t);
            return false;
        }
    }

    @Override
    public boolean canBuy(@NotNull Player player) {
        if (!super.canBuy(player)) {
            return false;
        }
        try {
            return this.delegate.canBuy(player);
        } catch (Throwable t) {
            // 无法判定就放行：真正的效果执行会在 onBuy 里被静默处理
            SRE.LOGGER.debug("Magician shop entry canBuy failed, allowing: {}", this.delegate.stack(), t);
            return true;
        }
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        try {
            boolean success = this.delegate.onBuy(player);
            if (success) {
                return true;
            }
            // 纯发物品类条目失败（背包满等）保留原语义：不扣钱，也不静默吞掉
            if (isPlainItemEntry(this.delegate)) {
                return false;
            }
            // 职业专属的特殊条目：魔术师身上可能完全没有对应数据（如能量），静默掉
            return true;
        } catch (Throwable t) {
            SRE.LOGGER.debug("Magician shop entry silently dropped: {}", this.delegate.stack(), t);
            return true;
        }
    }

    /** 只有「放物品 / 换价格」这类标准条目才保留失败语义。 */
    private static boolean isPlainItemEntry(ShopEntry entry) {
        Class<?> type = entry.getClass();
        return type == ShopEntry.class
                || type == KillerKnifeShopEntry.class
                || type == DiscountShopEntry.class
                || type == SteppedPriceShopEntry.class;
    }
}
