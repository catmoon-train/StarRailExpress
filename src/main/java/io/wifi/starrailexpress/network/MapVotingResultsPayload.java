/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.network;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

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