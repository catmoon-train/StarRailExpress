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
import io.wifi.starrailexpress.client.StatusBarHUD;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoveStatusBarPayload(String effect) implements CustomPacketPayload {
    public static final Type<RemoveStatusBarPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "remove_status_bar"));
    public static final StreamCodec<FriendlyByteBuf, RemoveStatusBarPayload> CODEC = StreamCodec.ofMember(RemoveStatusBarPayload::encode, RemoveStatusBarPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(effect);
    }

    public static RemoveStatusBarPayload decode(FriendlyByteBuf buf) {
        var effectHash = buf.readUtf();
        return new RemoveStatusBarPayload(effectHash);
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.client().execute(() -> {
                StatusBarHUD.getInstance().removeStatusBar(payload.effect());

            });
        });
    }
}
