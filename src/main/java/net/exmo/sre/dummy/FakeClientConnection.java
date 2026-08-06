/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package net.exmo.sre.dummy;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

/**
 * 假人专用的空连接：吞掉所有出站包，不处理任何入站包。
 * 参考 Carpet 假玩家实现，让服务端把假人当作真实连接玩家对待。
 */
public class FakeClientConnection extends Connection {

    public FakeClientConnection(PacketFlow flow) {
        super(flow);
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, Packet<?> packet) {
        // 假人不接收任何入站包
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        // 吞掉出站包
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
        // 吞掉出站包
    }
}
