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
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端在玩家成功释放技能后，向同世界玩家同步左侧 HUD 通告。
 */
public record SkillCastAnnouncePayload(
        String playerName,
        ResourceLocation roleId) implements CustomPacketPayload {

    public static final Type<SkillCastAnnouncePayload> ID = new Type<>(SRE.id("skill_cast_announce"));
    public static final StreamCodec<FriendlyByteBuf, SkillCastAnnouncePayload> CODEC =
            CustomPacketPayload.codec(SkillCastAnnouncePayload::write, SkillCastAnnouncePayload::new);

    public SkillCastAnnouncePayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(64), buffer.readResourceLocation());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(playerName == null ? "" : playerName, 64);
        buffer.writeResourceLocation(roleId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
