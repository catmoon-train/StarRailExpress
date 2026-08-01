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

package org.agmas.noellesroles.packet;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.HashMap;
import java.util.Map;

public record ChefCookC2SPacket(Map<Integer, Float> cookInfo) implements CustomPacketPayload {
    public static final Gson gson = new Gson();
    public static final ResourceLocation ABILITY_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "chef_cook_packet");
    public static final Type<ChefCookC2SPacket> ID = new Type<>(ABILITY_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ChefCookC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(gson.toJson(cookInfo));
    }

    public static ChefCookC2SPacket read(FriendlyByteBuf buf) {
        String data = buf.readUtf();
        Map<Integer, Float> cookInfos = new HashMap<>();
        java.lang.reflect.Type type = new TypeToken<Map<Integer, Float>>() {
        }.getType();
        try {
            cookInfos = gson.fromJson(data, type);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ChefCookC2SPacket(cookInfos);
    }

    static {
        CODEC = StreamCodec.ofMember(ChefCookC2SPacket::write, ChefCookC2SPacket::read);
    }
}