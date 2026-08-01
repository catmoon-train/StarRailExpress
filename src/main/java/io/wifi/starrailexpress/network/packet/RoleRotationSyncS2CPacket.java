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

package io.wifi.starrailexpress.network.packet;

import io.netty.buffer.ByteBuf;
import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record RoleRotationSyncS2CPacket(
        boolean isSelecting,
        int currentRoundIndex, // 当前轮次（从1开始）
        int totalPlayerCount,
        int confirmCountdown, // 确认倒计时（tick），-1 表示未激活
        int perPlayerTimeLimit, // 每个玩家的选择时限（tick）
        long roundStartTime, // 本轮开始的世界时间（tick）
        List<UUID> playerOrder, // 全局玩家顺序（按权重）
        Map<UUID, String> selectedRoles, // UUID -> 角色ID
        Set<UUID> randomChoosers,
        Map<UUID, List<String>> roundCandidates // 本轮玩家 -> 候选角色ID列表（最多3个）
) implements CustomPacketPayload {

    public static final Type<RoleRotationSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "role_rotation_sync"));

    // ----------------- 编解码器 -----------------
    private static final StreamCodec<ByteBuf, UUID> UUID_CODEC = StreamCodec.of(FriendlyByteBuf::writeUUID,
            FriendlyByteBuf::readUUID);

    private static final StreamCodec<ByteBuf, List<UUID>> UUID_LIST_CODEC = ByteBufCodecs.collection(ArrayList::new,
            UUID_CODEC, 256);

    private static final StreamCodec<ByteBuf, Map<UUID, String>> SELECTED_CODEC = ByteBufCodecs.map(HashMap::new,
            UUID_CODEC, ByteBufCodecs.STRING_UTF8, 256);

    private static final StreamCodec<ByteBuf, Set<UUID>> UUID_SET_CODEC = ByteBufCodecs.collection(HashSet::new,
            UUID_CODEC, 256);

    private static final StreamCodec<ByteBuf, Map<UUID, List<String>>> ROUND_CANDIDATES_CODEC = ByteBufCodecs.map(
            HashMap::new,
            UUID_CODEC,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, 3),
            256);

    public static final StreamCodec<ByteBuf, RoleRotationSyncS2CPacket> CODEC = new StreamCodec<>() {
        @Override
        public RoleRotationSyncS2CPacket decode(ByteBuf buf) {
            return new RoleRotationSyncS2CPacket(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),
                    UUID_LIST_CODEC.decode(buf),
                    SELECTED_CODEC.decode(buf),
                    UUID_SET_CODEC.decode(buf),
                    ROUND_CANDIDATES_CODEC.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, RoleRotationSyncS2CPacket pkt) {
            ByteBufCodecs.BOOL.encode(buf, pkt.isSelecting);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.currentRoundIndex);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.totalPlayerCount);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.confirmCountdown);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.perPlayerTimeLimit);
            ByteBufCodecs.VAR_LONG.encode(buf, pkt.roundStartTime);
            UUID_LIST_CODEC.encode(buf, pkt.playerOrder);
            SELECTED_CODEC.encode(buf, pkt.selectedRoles);
            UUID_SET_CODEC.encode(buf, pkt.randomChoosers);
            ROUND_CANDIDATES_CODEC.encode(buf, pkt.roundCandidates);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}