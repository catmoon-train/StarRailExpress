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

package io.wifi.starrailexpress.mixin.network;

import io.netty.channel.ChannelHandlerContext;
import io.wifi.starrailexpress.network.ConnectionTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 统计入口：在 {@link Connection} 这一层拦截收发，因此 mod 里所有发包方式
 * （{@code ServerPlayNetworking.send}、{@code PacketSender}、{@code player.connection.send}
 * 等，约 500 处）都会被覆盖，不需要各处自己埋点。
 *
 * <p>收与发都只处理自定义载荷包，原版 Minecraft 包一个 {@code instanceof} 就返回，不参与统计。
 * 侧别判定、记录开关与玩家归属都在 {@link ConnectionTracker} 里，这里只负责把载荷递进去。
 */
@Mixin(Connection.class)
public abstract class NetworkStatisticsMixin {

    @Unique
    private ConnectionTracker tmm$tracker;

    @Shadow
    @Nullable
    public abstract PacketListener getPacketListener();

    /** 每条连接一个，惰性创建以免依赖 mixin 的字段初始化注入。 */
    @Unique
    private ConnectionTracker tmm$tracker() {
        ConnectionTracker tracker = tmm$tracker;
        if (tracker == null) {
            tracker = new ConnectionTracker();
            tmm$tracker = tracker;
        }
        return tracker;
    }

    /** 本侧发出的数据包。 */
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"))
    private void tmm$trackSend(Packet<?> packet, PacketSendListener sendListener, CallbackInfo ci) {
        CustomPacketPayload payload = tmm$payloadOf(packet);
        if (payload != null) {
            tmm$tracker().record(payload, true, getPacketListener());
        }
    }

    /** 本侧收到的数据包。 */
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"))
    private void tmm$trackReceive(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        CustomPacketPayload payload = tmm$payloadOf(packet);
        if (payload != null) {
            tmm$tracker().record(payload, false, getPacketListener());
        }
    }

    @Unique
    @Nullable
    private static CustomPacketPayload tmm$payloadOf(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.payload();
        }
        if (packet instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.payload();
        }
        return null;
    }
}
