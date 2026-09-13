package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端 → 服务端：终端界面里按下回车后提交的指令原文。
 *
 * <p>
 * 服务端只做转发，真正的校验、解析与执行在
 * {@link org.agmas.noellesroles.role.bouns.roles.ProgrammerRole#executeTerminalCommand}。
 */
public record TerminalCommandC2SPacket(String command) implements CustomPacketPayload {
    public static final ResourceLocation TERMINAL_COMMAND_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "terminal_command");
    public static final Type<TerminalCommandC2SPacket> ID = new Type<>(TERMINAL_COMMAND_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalCommandC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.command()),
            buf -> new TerminalCommandC2SPacket(buf.readUtf()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
