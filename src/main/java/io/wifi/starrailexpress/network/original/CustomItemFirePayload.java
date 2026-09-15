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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.anticheat.ClickAntiCheat;
import io.wifi.starrailexpress.customitem.CustomItemData;
import io.wifi.starrailexpress.customitem.CustomItemLoader;
import io.wifi.starrailexpress.customitem.CustomItemRuntime;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 自定义枪械「左键发射」请求（C2S）。
 *
 * <p>
 * 左键打空气时原版不会向服务端发包，因此配置为「发射按键 = 左键」的枪械需要这条包才能开火，
 * 与狙击枪（{@link SniperShootPayload}）的按键开火路径同理。
 *
 * <p>
 * 服务端一律以自己手上的物品为准重新校验，只有确实是「左键发射」的自定义枪械才执行开火，
 * 避免客户端伪造。
 */
public record CustomItemFirePayload() implements CustomPacketPayload {
    public static final Type<CustomItemFirePayload> TYPE = new Type<>(SRE.id("custom_item_fire"));
    public static final StreamCodec<FriendlyByteBuf, CustomItemFirePayload> STREAM_CODEC = StreamCodec
            .unit(new CustomItemFirePayload());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<CustomItemFirePayload> {
        @Override
        public void receive(@NotNull CustomItemFirePayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            if (ClickAntiCheat.isLocked(player) || player.isSpectator()) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            CustomItemData data = CustomItemLoader.getData(stack);
            if (data == null || data.kind() != CustomItemData.Kind.GUN
                    || data.fireButton() != CustomItemData.FireButton.LEFT) {
                return;
            }
            CustomItemRuntime.useGun(player, stack, data);
        }
    }
}
