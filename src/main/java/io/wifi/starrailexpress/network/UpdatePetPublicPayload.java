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
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 切换「把随从展示给其他玩家」的请求（C2S）。
 * <p>
 * 只有这一个布尔量：随从皮肤本身沿用
 * {@link UpdateSkinSelectedPayload}（类型 {@code "pet"}），不需要另一套装备协议。
 * 服务端收到后写入 {@link SREPlayerSkinsComponent#setPetPublic(boolean)}，
 * 由随从装备管理器在下一轮扫描时广播出去。
 */
public record UpdatePetPublicPayload(boolean shared) implements CustomPacketPayload {

    public static final Type<UpdatePetPublicPayload> ID = new Type<>(SRE.id("update_pet_public"));
    public static final StreamCodec<FriendlyByteBuf, UpdatePetPublicPayload> CODEC = StreamCodec
            .ofMember(UpdatePetPublicPayload::encode, UpdatePetPublicPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(shared);
    }

    public static UpdatePetPublicPayload decode(FriendlyByteBuf buf) {
        return new UpdatePetPublicPayload(buf.readBoolean());
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) ->
                context.server().execute(() -> {
                    SREPlayerSkinsComponent component = SREPlayerSkinsComponent.KEY
                            .getNullable(context.player());
                    if (component == null) {
                        return;
                    }
                    component.setPetPublic(payload.shared());
                }));
    }
}
