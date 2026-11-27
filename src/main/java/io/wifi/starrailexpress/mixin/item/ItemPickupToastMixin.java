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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.ItemPickupToastPayload;
import io.wifi.starrailexpress.network.PacketTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 拾取物品提示（服务端）：在 {@link ItemEntity#playerTouch} 真正把物品塞进背包处挂钩，
 * 把「本次实际进了背包的那部分」发给拾取者本人，由客户端 {@code ItemPickupToastHud} 显示。
 *
 * <h2>为什么挂在 {@code Inventory.add} 的返回处</h2>
 * <p>
 * 在 {@code playerTouch} 的 HEAD 注入不行：那时还不知道这一叠有没有被装下，
 * 背包满、被角色逻辑拦下、{@code pickupDelay} 未到都会走到同一个 HEAD，
 * 结果就是「提示了但其实没捡到」。
 *
 * <p>
 * {@code Inventory#add} 会直接改传入栈的数量（装下的部分从栈里扣掉），
 * 所以「进包前数量 − 进包后数量」正好等于本次真正获得的数量，
 * 一叠只装了一半也不会多报。走 WrapOperation 而不是在 RETURN 注入，
 * 是为了拿到这次调用的实参，不必再反射读局部变量表。
 *
 * <p>
 * 这里只处理原版 {@link ItemEntity} 的拾取。其它自定义落物实体（飞刀、车轮等）
 * 各自在自己的 {@code playerTouch} 里加物品，不属于「捡地上的物品」，故意不拦。
 */
@Mixin(ItemEntity.class)
public class ItemPickupToastMixin {

    @WrapOperation(method = "playerTouch", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean sre$notifyPickupToast(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        // 客户端不同步、非玩家背包（理论上不会）直接放过。
        if (!(inventory.player instanceof ServerPlayer player)) {
            return original.call(inventory, stack);
        }
        // 只有对局进行中才提示：大厅、观战、训练场里捡东西不该冒提示。
        if (!GameUtils.isGameRunning(player)) {
            return original.call(inventory, stack);
        }

        // 模板必须在调用前取：add 装下整叠时会 copyAndClear，把实参栈清空。
        ItemStack template = stack.copyWithCount(1);
        int before = stack.getCount();
        boolean added = original.call(inventory, stack);
        int acquired = before - stack.getCount();
        if (!added || acquired <= 0 || template.isEmpty()) {
            return added;
        }

        // 组件（自定义名字 / 附魔 / 染色等）跟着模板一起带上，
        // 客户端才能显示和物品栏一致的图标与名字。
        PacketTracker.sendToClient(player, new ItemPickupToastPayload(template.copyWithCount(acquired)));
        return added;
    }
}
