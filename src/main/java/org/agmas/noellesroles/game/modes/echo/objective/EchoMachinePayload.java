package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView;
import java.util.UUID;

/** 每个同步周期只给本人一台可见近距机器；空投影立即撤销上一个提示。 */
public record EchoMachinePayload(UUID match, ResourceLocation dimension, EchoMachineView machine) implements CustomPacketPayload {
    public static final Type<EchoMachinePayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/machine"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoMachinePayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); b.writeBoolean(p.machine() != null);
        if (p.machine() == null) return;
        var m = p.machine();
        b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeEnum(m.type()); b.writeUtf(m.id(), 64);
        b.writeEnum(m.status()); b.writeVarInt(m.amount()); b.writeVarInt(m.required()); b.writeVarInt(m.progress()); b.writeVarInt(m.remaining());
    }, b -> {
        var match = b.readUUID(); var dimension = b.readResourceLocation();
        var machine = b.readBoolean() ? new EchoMachineView(b.readInt(), b.readInt(), b.readInt(), b.readEnum(EchoMachineView.Type.class),
                b.readUtf(64), b.readEnum(EchoMachineView.Status.class), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt()) : null;
        return new EchoMachinePayload(match, dimension, machine);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
