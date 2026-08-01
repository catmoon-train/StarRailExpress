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

package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.cca.ExtraSlotComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map.Entry;
import java.util.function.Predicate;

@Mixin(Inventory.class)
public class InventoryClearMixin {
    @Shadow
    @Final
    public Player player;

    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"), cancellable = true)
    private void onClearOrCountMatchingItems(
            Predicate<ItemStack> predicate,
            int i,
            Container container,
            CallbackInfoReturnable<Integer> cir) {
        var esc = ExtraSlotComponent.KEY.get(player);
        int customCount = 0;
        int originalCount = cir.getReturnValueI();
        int remaining = i;
        if (i > 0) {
            remaining -= originalCount;
        }
        if (esc.SLOTS == null)
            return;
        for (Entry<ResourceLocation, ItemStack> entry : esc.SLOTS.entrySet()) {
            var slot = entry.getKey();
            var stack = entry.getValue();
            if (stack.isEmpty())
                continue;
            if (!predicate.test(stack))
                continue;
            if (i == 0) {
                // 仅计数模式：直接累加数量，不修改物品
                customCount += stack.getCount();
            } else {
                // 清除模式
                int toRemove;
                if (i < 0) {
                    // i == -1 → 清除全部
                    toRemove = stack.getCount();
                } else {
                    // i > 0 → 最多再清除 remaining 个
                    if (remaining <= 0)
                        break;
                    toRemove = Math.min(stack.getCount(), remaining);
                }

                customCount += toRemove;
                remaining -= toRemove;

                // 实际从槽位扣除
                stack.shrink(toRemove);
                if (stack.isEmpty()) {
                    esc.SLOTS.remove(slot);
                }
                if (i > 0 && remaining <= 0)
                    break; // 配额已满，提前结束
            }
        }
        if (i != 0 && customCount != 0) {
            esc.fullSync();
        }
        if (customCount != 0) {
            cir.setReturnValue(originalCount + customCount);
        }
    }
}
