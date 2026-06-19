package io.wifi.starrailexpress.sponsor;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：同步当前赞助者 plush 名单（每个名字对应一个 {@code <name>_plush}）。
 * 客户端据此在游戏介绍 GUI 中列出赞助者 plush。
 */
public record SponsorListPayload(List<String> names) implements CustomPacketPayload {
    public static final Type<SponsorListPayload> ID = new Type<>(SRE.id("sponsor_list_sync"));
    public static final StreamCodec<FriendlyByteBuf, SponsorListPayload> CODEC =
            CustomPacketPayload.codec(SponsorListPayload::write, SponsorListPayload::new);

    private SponsorListPayload(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static List<String> read(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buffer.readUtf());
        }
        return list;
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(names.size());
        for (String name : names) {
            buffer.writeUtf(name);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
