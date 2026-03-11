package org.agmas.noellesroles.packet;

import java.util.HashMap;
import java.util.Map;

import org.agmas.noellesroles.Noellesroles;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

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