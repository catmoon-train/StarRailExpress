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
import io.wifi.starrailexpress.client.StaminaRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TriggerScreenEdgeEffectPayload(int color, long durationMs, float intensity)
        implements CustomPacketPayload {
    public static final Type<TriggerScreenEdgeEffectPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "trigger_screen_edge_effect"));
    public static final StreamCodec<FriendlyByteBuf, TriggerScreenEdgeEffectPayload> CODEC = StreamCodec
            .ofMember(TriggerScreenEdgeEffectPayload::encode, TriggerScreenEdgeEffectPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(color);
        buf.writeLong(durationMs);
        buf.writeFloat(intensity);
    }

    public static TriggerScreenEdgeEffectPayload decode(FriendlyByteBuf buf) {
        int color = buf.readInt();
        long durationMs = buf.readLong();
        float intensity = buf.readFloat();
        return new TriggerScreenEdgeEffectPayload(color, durationMs, intensity);
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.client().execute(() -> {
                StaminaRenderer.triggerScreenEdgeEffect(payload.color, payload.durationMs, payload.intensity);
            });
        });
    }
}
