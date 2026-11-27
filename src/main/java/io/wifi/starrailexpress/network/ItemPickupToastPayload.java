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

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * 拾取物品提示：服务端只把「这一叠实际进了背包的那部分」发给拾取它的本人。
 *
 * <p>
 * 载荷直接带 {@link ItemStack}（含自定义名字 / 组件），客户端才能拿到和物品栏一致的
 * 图标与显示名；数量写在 stack 的 count 上，不再单开一个字段。
 * 合并同名物品由客户端 HUD 自己累积（{@code ItemPickupToastHud}），服务端不做聚合，
 * 免得为「连捡同一物品」再维护一份每人每物品的状态。
 */
public record ItemPickupToastPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<ItemPickupToastPayload> ID = new Type<>(SRE.id("item_pickup_toast"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickupToastPayload> CODEC = StreamCodec
            .ofMember(ItemPickupToastPayload::write, ItemPickupToastPayload::read);

    /** 防御性拷贝：载荷可能在别的线程被读，不能让调用方的栈被后续逻辑改掉。 */
    public ItemPickupToastPayload {
        stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
    }

    private static ItemPickupToastPayload read(RegistryFriendlyByteBuf buffer) {
        return new ItemPickupToastPayload(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
