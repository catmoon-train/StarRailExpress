package io.wifi.starrailexpress.network;

import com.mojang.serialization.Codec;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

public class MapVotingResultsPayload implements CustomPacketPayload {
    public static final Type<MapVotingResultsPayload> TYPE = new Type<>(
            SRE.id("map_voting_results")
    );
    
    public static final StreamCodec<FriendlyByteBuf, MapVotingResultsPayload> CODEC = StreamCodec.ofMember(
            MapVotingResultsPayload::write,
            MapVotingResultsPayload::new
    );

    public final String result;



    // 用于解码的构造函数
    public MapVotingResultsPayload(FriendlyByteBuf buf) {
        this.result = buf.readUtf();
    }
    public MapVotingResultsPayload(String s) {
        this.result = s;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf( result);
    }


    @Override
    public Type<MapVotingResultsPayload> type() {
        return TYPE;
    }
}