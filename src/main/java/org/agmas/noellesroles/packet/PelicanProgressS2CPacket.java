package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PelicanProgressS2CPacket(boolean show, int eaten, int required, List<BellyEntry> belly) implements CustomPacketPayload {
    public static final ResourceLocation PELICAN_PROGRESS_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pelican_progress");
    public static final Type<PelicanProgressS2CPacket> ID = new Type<>(PELICAN_PROGRESS_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PelicanProgressS2CPacket> CODEC;

    public PelicanProgressS2CPacket {
        belly = belly == null ? List.of() : List.copyOf(belly);
    }

    public static PelicanProgressS2CPacket clear() {
        return new PelicanProgressS2CPacket(false, 0, 1, List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(show);
        buf.writeInt(eaten);
        buf.writeInt(required);
        buf.writeInt(belly.size());
        for (BellyEntry entry : belly) {
            buf.writeUUID(entry.playerId());
            buf.writeUtf(entry.name(), 64);
        }
    }

    public static PelicanProgressS2CPacket read(FriendlyByteBuf buf) {
        boolean show = buf.readBoolean();
        int eaten = buf.readInt();
        int required = buf.readInt();
        int size = Math.max(0, Math.min(32, buf.readInt()));
        List<BellyEntry> belly = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            belly.add(new BellyEntry(buf.readUUID(), buf.readUtf(64)));
        }
        return new PelicanProgressS2CPacket(show, eaten, required, belly);
    }

    static {
        CODEC = StreamCodec.ofMember(PelicanProgressS2CPacket::write, PelicanProgressS2CPacket::read);
    }

    public record BellyEntry(UUID playerId, String name) {
        public BellyEntry {
            name = name == null ? "" : name;
        }
    }
}
