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

/**
 * 志愿海选模式同步包（逐玩家发送，因为「海选池」的揭晓状态是因人而异的）。
 *
 * <p>
 * 字段含义见 {@code VolunteerOpenDraftState}。{@code phase == 0} 表示该模式已结束，
 * 客户端应清理状态并关闭界面。
 */
public record VolunteerOpenSyncS2CPacket(
        int phase, // 0 = 已结束，1 = 志愿阶段，2 = 海选阶段，3 = 确认阶段
        int totalPlayers,
        int remainingTicks, // 当前阶段剩余时间（tick）；-1 表示「等待玩家打开界面，尚未开始计时」
        int phaseTimeLimit, // 当前阶段时限（tick）
        int confirmCountdown, // 确认倒计时（tick），-1 表示未激活
        boolean confirmRequired, // 确认阶段是否要求手动确认
        List<UUID> playerOrder, // 全局玩家序号
        Map<UUID, String> pickedRoles, // 玩家 -> 职业 id（"" 表示显示为随机），缺失表示未选
        Set<UUID> confirmedPlayers, // 已手动确认的玩家
        int poolSize, // 海选池大小
        int poolRows, // 海选池行数
        int poolCols, // 海选池列数
        Map<Integer, String> visibleRoles, // 对我可见的：池下标 -> 职业 id
        Set<Integer> cardRevealed, // 由卡牌额外揭示（加粗橙色）
        Set<Integer> hiddenRevealed, // 可见但名字要显示为「隐藏职业」
        int myPickIndex, // 我选中的池下标（-1 未选）
        Set<Integer> chosenIndices, // 所有已被选择的池下标
        int myVolunteerIndex, // 我的志愿职业在池中的下标（-1 表示场上没有）
        int currentGroup, // 当前是第几组（1 起，0 表示无）
        Set<UUID> currentGroupMembers, // 当前组内的玩家
        int totalGroups, // 总组数
        boolean canSelect, // 我现在是否可以挑选
        String myVolunteerRoleId // 我的志愿职业 id（"" 表示未提交）
) implements CustomPacketPayload {

    public static final Type<VolunteerOpenSyncS2CPacket> TYPE = new Type<>(
            ResourceLocation.tryBuild(SRE.MOD_ID, "volunteer_open_sync"));

    private static final StreamCodec<ByteBuf, UUID> UUID_CODEC = StreamCodec.of(FriendlyByteBuf::writeUUID,
            FriendlyByteBuf::readUUID);

    private static final StreamCodec<ByteBuf, List<UUID>> UUID_LIST_CODEC = ByteBufCodecs.collection(ArrayList::new,
            UUID_CODEC, 256);

    private static final StreamCodec<ByteBuf, Set<UUID>> UUID_SET_CODEC = ByteBufCodecs.collection(HashSet::new,
            UUID_CODEC, 256);

    private static final StreamCodec<ByteBuf, Map<UUID, String>> PICKED_CODEC = ByteBufCodecs.map(HashMap::new,
            UUID_CODEC, ByteBufCodecs.STRING_UTF8, 256);

    private static final StreamCodec<ByteBuf, Map<Integer, String>> VISIBLE_CODEC = ByteBufCodecs.map(HashMap::new,
            ByteBufCodecs.VAR_INT, ByteBufCodecs.STRING_UTF8, 512);

    private static final StreamCodec<ByteBuf, Set<Integer>> INT_SET_CODEC = ByteBufCodecs.collection(HashSet::new,
            ByteBufCodecs.VAR_INT, 512);

    public static final StreamCodec<ByteBuf, VolunteerOpenSyncS2CPacket> CODEC = new StreamCodec<>() {
        @Override
        public VolunteerOpenSyncS2CPacket decode(ByteBuf buf) {
            return new VolunteerOpenSyncS2CPacket(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    UUID_LIST_CODEC.decode(buf),
                    PICKED_CODEC.decode(buf),
                    UUID_SET_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    VISIBLE_CODEC.decode(buf),
                    INT_SET_CODEC.decode(buf),
                    INT_SET_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    INT_SET_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    UUID_SET_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, VolunteerOpenSyncS2CPacket pkt) {
            ByteBufCodecs.VAR_INT.encode(buf, pkt.phase);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.totalPlayers);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.remainingTicks);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.phaseTimeLimit);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.confirmCountdown);
            ByteBufCodecs.BOOL.encode(buf, pkt.confirmRequired);
            UUID_LIST_CODEC.encode(buf, pkt.playerOrder);
            PICKED_CODEC.encode(buf, pkt.pickedRoles);
            UUID_SET_CODEC.encode(buf, pkt.confirmedPlayers);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.poolSize);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.poolRows);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.poolCols);
            VISIBLE_CODEC.encode(buf, pkt.visibleRoles);
            INT_SET_CODEC.encode(buf, pkt.cardRevealed);
            INT_SET_CODEC.encode(buf, pkt.hiddenRevealed);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.myPickIndex);
            INT_SET_CODEC.encode(buf, pkt.chosenIndices);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.myVolunteerIndex);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.currentGroup);
            UUID_SET_CODEC.encode(buf, pkt.currentGroupMembers);
            ByteBufCodecs.VAR_INT.encode(buf, pkt.totalGroups);
            ByteBufCodecs.BOOL.encode(buf, pkt.canSelect);
            ByteBufCodecs.STRING_UTF8.encode(buf, pkt.myVolunteerRoleId);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
