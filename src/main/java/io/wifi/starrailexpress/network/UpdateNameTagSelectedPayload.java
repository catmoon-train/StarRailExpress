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
import net.exmo.sre.nametag.NameTagInventoryComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateNameTagSelectedPayload(String nameTag) implements CustomPacketPayload {
    public static final Type<UpdateNameTagSelectedPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "update_nametag_selected"));
    public static final StreamCodec<FriendlyByteBuf, UpdateNameTagSelectedPayload> CODEC = StreamCodec.ofMember(
            UpdateNameTagSelectedPayload::encode,
            UpdateNameTagSelectedPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(nameTag);
    }

    public static UpdateNameTagSelectedPayload decode(FriendlyByteBuf buf) {
        return new UpdateNameTagSelectedPayload(buf.readUtf());
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.server().execute(() -> {
                NameTagInventoryComponent nameTagComponent = NameTagInventoryComponent.KEY.get(context.player());
                if (!nameTagComponent.nameTags.contains(payload.nameTag()))
                    return;
                nameTagComponent.setCurrentNameTag(payload.nameTag());
            });
        });
    }
}
