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
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.data.PlayerSkinEconomyManager;
import io.wifi.starrailexpress.util.ItemSkinManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateSkinSelectedPayload(String id, String name) implements CustomPacketPayload {
    public static final Type<UpdateSkinSelectedPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "update_skin_selected"));
    public static final StreamCodec<FriendlyByteBuf, UpdateSkinSelectedPayload> CODEC = StreamCodec
            .ofMember(UpdateSkinSelectedPayload::encode, UpdateSkinSelectedPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeUtf(name);

    }

    public static UpdateSkinSelectedPayload decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        return new UpdateSkinSelectedPayload(id, name);
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> {
            context.server().execute(() -> {
                SREPlayerSkinsComponent skincca = SREPlayerSkinsComponent.KEY.get(context.player());
                boolean random = ItemSkinManager.isRandomSkin(payload.name);
                if (!random
                        && !skincca.isSkinUnlockedForItemType(payload.id, payload.name) && !PlayerSkinEconomyManager
                                .isSkinUnlockedForItemType(context.player(), payload.id, payload.name)) {
                    return;
                }
                PlayerSkinEconomyManager.setEquippedSkinForItemType(context.player(), payload.id, payload.name);
                if (random) {
                    // 选中「随机」时立刻抽一次，避免本局剩余时间一直显示未装备；
                    // 之后每局开局会由 SkinRandomizer 重新抽取。
                    skincca.rollRandomSkin(payload.id);
                }
            });
        });
    }
}
