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

package org.agmas.noellesroles.packet.Loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

public record LootMultiResultS2CPacket(int poolID, List<int[]> results) implements CustomPacketPayload {
    public static final ResourceLocation LOOT_MULTI_RESULT_PAYLOAD_ID =
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "loot_multi_result");
    public static final Type<LootMultiResultS2CPacket> ID = new Type<>(LOOT_MULTI_RESULT_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, LootMultiResultS2CPacket> CODEC;
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(poolID);
        buf.writeInt(results.size());
        for (int[] result : results) {
            buf.writeInt(result[0]);
            buf.writeInt(result[1]);
        }
    }

    public static LootMultiResultS2CPacket read(FriendlyByteBuf buf) {
        int poolID = buf.readInt();
        int count = buf.readInt();
        if (count < 0 || count > 10) {
            count = 0;
        }
        List<int[]> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(new int[]{buf.readInt(), buf.readInt()});
        }
        return new LootMultiResultS2CPacket(poolID, results);
    }
    static {
        CODEC = StreamCodec.ofMember(LootMultiResultS2CPacket::write, LootMultiResultS2CPacket::read);
    }
}
