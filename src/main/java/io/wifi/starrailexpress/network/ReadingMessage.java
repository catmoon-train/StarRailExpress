package io.wifi.starrailexpress.network;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public class ReadingMessage implements CustomPacketPayload {
    private final static ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "reading_book");
    public static final CustomPacketPayload.Type<ReadingMessage> TYPE = new CustomPacketPayload.Type<>(PACKET_ID);
    public static final StreamCodec<FriendlyByteBuf, ReadingMessage> STREAM_CODEC = StreamCodec.unit(
            new ReadingMessage()
    );


    public ReadingMessage() {

    }



    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}