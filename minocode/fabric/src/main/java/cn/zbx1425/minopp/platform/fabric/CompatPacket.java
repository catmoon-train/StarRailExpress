package cn.zbx1425.minopp.platform.fabric;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public class CompatPacket {

    public final ResourceLocation id;

    public CompatPacket(ResourceLocation id) {
        this.id = id;
        this.TYPE = new CustomPacketPayload.Type<>(id);
    }

    public class Payload implements CustomPacketPayload {

        public final FriendlyByteBuf buffer;

        public Payload(FriendlyByteBuf buffer) {
            this.buffer = buffer;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public final CustomPacketPayload.Type<Payload> TYPE;

    public final StreamCodec<ByteBuf, Payload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf dest, Payload src) {
            dest.writeBytes(src.buffer, 0, src.buffer.writerIndex());
        }

        @Override
        public Payload decode(ByteBuf src) {
            ByteBuf data = src.retainedDuplicate();
            src.readerIndex(src.writerIndex());
            return new Payload(new FriendlyByteBuf(data));
        }
    };
}