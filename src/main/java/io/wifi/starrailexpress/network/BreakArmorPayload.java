package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BreakArmorPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final ResourceLocation BREAK_ARMOR_ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID,
            "break_armor");
    public static final Type<BreakArmorPayload> ID = new Type<>(BREAK_ARMOR_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, BreakArmorPayload> CODEC;

    public BreakArmorPayload(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);

    }

    public static BreakArmorPayload read(FriendlyByteBuf buf) {
        return new BreakArmorPayload(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    static {
        CODEC = StreamCodec.ofMember(BreakArmorPayload::write, BreakArmorPayload::read);
    }
}