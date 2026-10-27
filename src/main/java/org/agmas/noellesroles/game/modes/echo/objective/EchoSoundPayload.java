package org.agmas.noellesroles.game.modes.echo.objective;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** 全图提交音仅传位置，不传真实/伪造身份、任意音源或音量。 */
public record EchoSoundPayload(UUID match,ResourceLocation dimension,Vec3 origin,long seed) implements CustomPacketPayload {
    public EchoSoundPayload {
        Objects.requireNonNull(match); Objects.requireNonNull(dimension); Objects.requireNonNull(origin);
        if(!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z) || Math.abs(origin.x)>30000000 || Math.abs(origin.z)>30000000 || Math.abs(origin.y)>30000000) throw new IllegalArgumentException("无效回声声源");
    }
    public static final Type<EchoSoundPayload> ID=new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles","echo/submission_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EchoSoundPayload> CODEC=StreamCodec.of((b,p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); b.writeDouble(p.origin().x); b.writeDouble(p.origin().y); b.writeDouble(p.origin().z); b.writeLong(p.seed());
    },b -> new EchoSoundPayload(b.readUUID(),b.readResourceLocation(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
