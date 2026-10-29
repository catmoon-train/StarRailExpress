package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 同维度公开目标快照；与本人RoleData私有投影隔离。 */
public record EchoPublicPayload(UUID match,ResourceLocation dimension,EchoPublicState state) implements CustomPacketPayload {
    public static final Type<EchoPublicPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/public_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoPublicPayload> CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); var s=p.state();
        b.writeEnum(s.phase()); b.writeVarLong(s.tick()); b.writeVarInt(s.remaining()); b.writeBoolean(s.paused());
        b.writeVarInt(s.escaped()); b.writeVarInt(s.quota()); b.writeBoolean(s.coreOpen()); b.writeVarLong(s.coreUntil());
        b.writeBoolean(s.evacuationOpen()); b.writeVarLong(s.closedUntil()); b.writeBoolean(s.overloadUsed());
        b.writeVarInt(s.beacons().size());
        for(var v:s.beacons()) {
            b.writeUtf(v.id(),64); b.writeVarInt(v.delivered()); b.writeVarInt(v.required()); b.writeVarInt(v.progress());
            b.writeBoolean(v.active()); b.writeVarLong(v.stalledUntil());
            b.writeBoolean(v.position()!=null);
            if(v.position()!=null) { b.writeDouble(v.position().x()); b.writeDouble(v.position().y()); b.writeDouble(v.position().z()); }
            b.writeVarLong(v.submissionUntil());
        }
    },b -> {
        var match=b.readUUID(); var dimension=b.readResourceLocation(); var phase=b.readEnum(EchoPhase.class);
        long tick=b.readVarLong(); int remaining=b.readVarInt(); boolean paused=b.readBoolean();
        int escaped=b.readVarInt(),quota=b.readVarInt(); boolean core=b.readBoolean(); long coreUntil=b.readVarLong();
        boolean evacuation=b.readBoolean(); long closed=b.readVarLong(); boolean used=b.readBoolean(); int size=b.readVarInt();
        if(size<0 || size>3) throw new IllegalArgumentException("公开信标数量超限");
        var beacons=new ArrayList<EchoPublicState.Beacon>(size);
        for(int n=0;n<size;n++) {
            String name=b.readUtf(64); int delivered=b.readVarInt(),required=b.readVarInt(),progress=b.readVarInt();
            boolean active=b.readBoolean(); long stalled=b.readVarLong();
            var point=b.readBoolean()?new EchoPublicState.Point(b.readDouble(),b.readDouble(),b.readDouble()):null;
            beacons.add(new EchoPublicState.Beacon(name,delivered,required,progress,active,stalled,point,b.readVarLong()));
        }
        return new EchoPublicPayload(match,dimension,new EchoPublicState(phase,tick,remaining,paused,escaped,quota,core,coreUntil,evacuation,closed,used,beacons));
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
