package org.agmas.noellesroles.packet;

import java.util.ArrayList;
import java.util.List;
import org.agmas.noellesroles.Noellesroles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 单台机器的公开历史，按时间从旧到新排列，不使用 NBT 编解码。 */
public record IdentityScannerStateS2CPacket(BlockPos pos, int cooldownTicks, List<Entry> history)
        implements CustomPacketPayload {
    public static final int MAX_HISTORY = 256;
    public static final int MAX_NAME = 64;
    public static final int MAX_ROLE_ID = 256;
    public static final Type<IdentityScannerStateS2CPacket> ID = new Type<>(Noellesroles.id("identity_scanner_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IdentityScannerStateS2CPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.cooldownTicks());
                buf.writeVarInt(packet.history().size());
                for (Entry entry : packet.history()) {
                    buf.writeUtf(entry.name(), MAX_NAME);
                    buf.writeUtf(entry.roleId(), MAX_ROLE_ID);
                    buf.writeInt(entry.color());
                }
            }, buf -> {
                BlockPos pos = buf.readBlockPos();
                int cooldown = buf.readVarInt();
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_HISTORY) {
                    throw new IllegalArgumentException("Invalid identity scanner history size: " + size);
                }
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buf.readUtf(MAX_NAME), buf.readUtf(MAX_ROLE_ID), buf.readInt()));
                }
                return new IdentityScannerStateS2CPacket(pos, cooldown, entries);
            });

    public IdentityScannerStateS2CPacket {
        pos = pos.immutable();
        cooldownTicks = Math.max(0, cooldownTicks);
        if (history.size() > MAX_HISTORY) {
            throw new IllegalArgumentException("Identity scanner history exceeds " + MAX_HISTORY);
        }
        history = List.copyOf(history);
    }

    public record Entry(String name, String roleId, int color) {
        public Entry {
            if (name.length() > MAX_NAME || roleId.length() > MAX_ROLE_ID) {
                throw new IllegalArgumentException("Identity scanner entry exceeds string limits");
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
