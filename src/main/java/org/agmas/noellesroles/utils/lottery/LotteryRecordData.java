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

package org.agmas.noellesroles.utils.lottery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.UUID;

/**
 * 玩家抽奖记录数据类
 * - 存储玩家识别信息
 * - 存储玩家抽奖次数及记录
 */
public class LotteryRecordData{
    // 抽奖记录项目
    public static class LotteryItemData {
        protected LotteryItemData(int poolId, int quality, String name, long timeStamp) {
            this.poolId = poolId;
            this.quality = quality;
            this.name = name;
            this.timeStamp = timeStamp;
        }
        public static LotteryItemData fromNbt(CompoundTag tag) {
            return new LotteryItemData(
                    tag.getInt("Result PoolID"),
                    tag.getInt("Result Quality"),
                    tag.getString("Result Name"),
                    tag.getLong("Time Stamp")
            );
        }
        /** NBT 化*/
        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Result PoolID", poolId);
            tag.putInt("Result Quality", quality);
            tag.putString("Result Name", name);
            tag.putLong("Time Stamp", timeStamp);
            return tag;
        }
        /** 抽到的物品卡池 id */
        protected final int poolId;
        /** 抽到的物品品质 */
        protected final int quality;
        /** 抽到的物品名称 */
        protected final String name;
        protected final long timeStamp;
    }
    protected LotteryRecordData(int lotteryChance, UUID playerUuid, ArrayList<LotteryItemData> lotteryItems) {
        this.lotteryChance = lotteryChance;
        this.uuid = playerUuid;
        this.lotteryItems = lotteryItems;
    }
    public static LotteryRecordData fromNbt(CompoundTag tag)
    {
        LotteryRecordData data = new LotteryRecordData(
                tag.getInt("Lottery Chance"),
                tag.getUUID("Player UUID"),
                new ArrayList<>()
        );
        // Load History
        ListTag historyTag = tag.getList("Lottery History", Tag.TAG_COMPOUND);
        for(int i = 0; i < historyTag.size(); ++i)
        {
            CompoundTag itemTag = historyTag.getCompound(i);
            data.lotteryItems.add(LotteryItemData.fromNbt(itemTag));
        }
        return data;
    }
    /** NBT 化*/
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Player UUID", uuid);
        tag.putInt("Lottery Chance", lotteryChance);
        // 添加历史记录
        ListTag historyTag = new ListTag();
        for(LotteryItemData item : lotteryItems)
        {
            historyTag.add(item.toNbt());
        }
        tag.put("Lottery History", historyTag);
        return tag;
    }

    protected ArrayList<LotteryItemData> lotteryItems = new ArrayList<>();// 历史记录
    protected UUID uuid;
    protected int lotteryChance = 0;
}
