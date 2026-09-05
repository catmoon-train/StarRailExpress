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

/**
 * 任务完成提醒：只同步标题和奖励数字，避免再拉一整份通行证 JSON。
 */
public record ProgressionQuestToastPayload(
        String title,
        int experience,
        int coins,
        int loot) implements CustomPacketPayload {

    public static final Type<ProgressionQuestToastPayload> ID = new Type<>(SRE.id("progression_quest_toast"));
    public static final StreamCodec<FriendlyByteBuf, ProgressionQuestToastPayload> CODEC =
            CustomPacketPayload.codec(ProgressionQuestToastPayload::write, ProgressionQuestToastPayload::new);

    public ProgressionQuestToastPayload(FriendlyByteBuf buffer) {
        this(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(title == null ? "" : title, 256);
        buffer.writeVarInt(experience);
        buffer.writeVarInt(coins);
        buffer.writeVarInt(loot);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
