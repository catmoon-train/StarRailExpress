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

import io.wifi.starrailexpress.customitem.CustomItemCooldownKeys;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ServerItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 只为「自定义列车物品的专属冷却键」挡掉原版的冷却同步包。
 *
 * <p>
 * <b>为什么必须在这里</b>：服务端玩家的冷却表其实是子类
 * {@link ServerItemCooldowns}（{@code ServerPlayer#createItemCooldowns} 返回它），
 * 它<b>覆写</b>了 {@code onCooldownStarted} / {@code onCooldownEnded} 来发
 * {@code ClientboundCooldownPacket}（基类里这两个方法经核对是空实现）。
 * 而自定义物品的冷却键是<b>未注册</b>的 {@link Item}
 * （见 {@link CustomItemCooldownKeys}），那个包要按 {@code ResourceLocation} 序列化，
 * 取不到 id 会直接抛异常 —— 所以命中我们的键时必须取消，让
 * {@link io.wifi.starrailexpress.network.CustomItemCooldownS2CPayload} 接手同步。
 *
 * <p><b>影响范围</b>：只有 {@link CustomItemCooldownKeys#isCustomKey(Item)} 为 true 的键会被取消，
 * 也就是我们自己造的那几把。其余所有物品（模组物品、原版物品、其它附属的物品）
 * 的冷却同步走的还是原版那条路，行为与改动前完全一致。
 */
@Mixin(ServerItemCooldowns.class)
public class ServerItemCooldownsMixin {

    @Inject(method = "onCooldownStarted", at = @At("HEAD"), cancellable = true)
    private void sre$skipVanillaCooldownStartPacket(Item item, int ticks, CallbackInfo ci) {
        if (CustomItemCooldownKeys.isCustomKey(item)) {
            ci.cancel();
        }
    }

    @Inject(method = "onCooldownEnded", at = @At("HEAD"), cancellable = true)
    private void sre$skipVanillaCooldownEndPacket(Item item, CallbackInfo ci) {
        if (CustomItemCooldownKeys.isCustomKey(item)) {
            ci.cancel();
        }
    }
}