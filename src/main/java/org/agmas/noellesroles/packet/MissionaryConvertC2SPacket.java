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

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.killer.missionary.MissionaryPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;

import java.util.UUID;

/**
 * 客户端 -> 服务端：传教士在背包人物头像界面选择目标后释放传教（convert）。
 */
public record MissionaryConvertC2SPacket(UUID targetId) implements CustomPacketPayload {
    public static final ResourceLocation ID_LOC = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "missionary_convert_c2s");
    public static final CustomPacketPayload.Type<MissionaryConvertC2SPacket> ID =
            new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<RegistryFriendlyByteBuf, MissionaryConvertC2SPacket> CODEC =
            StreamCodec.ofMember(MissionaryConvertC2SPacket::encode, MissionaryConvertC2SPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(targetId().toString());
    }

    public static MissionaryConvertC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new MissionaryConvertC2SPacket(UUID.fromString(buf.readUtf()));
    }

    public static void handle(MissionaryConvertC2SPacket packet, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (player.hasEffect(ModEffects.SAFE_TIME)) return;
        ServerPlayer target = player.server.getPlayerList().getPlayer(packet.targetId());
        if (target == null) return;
        MissionaryPlayerComponent.KEY.get(player).convert(target);
    }
}
