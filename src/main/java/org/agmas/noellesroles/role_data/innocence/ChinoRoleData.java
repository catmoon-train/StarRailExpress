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

package org.agmas.noellesroles.role_data.innocence;

import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 卡布奇诺咖啡师的职业数据：目前只保存「抱兔兔」的冷却。
 * <p>
 * 乘骑本身的状态在 {@link org.agmas.noellesroles.role.anime.chino.ChinoHeadRideManager}
 * 里按载具 UUID 记着（要能扛住咖啡师死亡/退出），这里只放需要同步给客户端显示 HUD 的冷却。
 * <p>
 * 冷却终点用「开局以来的刻数」表示（{@link GameUtils#getTicksFromGameStart}），
 * 会议/时停期间会暂停，与技能系统一致；客户端读同一个时钟所以剩余秒数两边一致。
 */
public class ChinoRoleData extends SimpleRoleData {

    /** 抱人冷却结束的游戏刻；0 表示不在冷却。 */
    public long rideCooldownEnd = 0;

    public ChinoRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        // 冷却只需要咖啡师自己的客户端知道（HUD 显示）
        return this.player == player;
    }

    /** 剩余冷却 tick（客户端同样可算）。 */
    public long getRideCooldownLeft() {
        if (rideCooldownEnd <= 0) {
            return 0;
        }
        return Math.max(0, rideCooldownEnd - GameUtils.getTicksFromGameStart(this.player.level()));
    }

    public boolean isRideOnCooldown() {
        return getRideCooldownLeft() > 0;
    }

    /** 开始抱人冷却（只在成功抱起来时调用，会同步一次给客户端）。 */
    public void startRideCooldown(int ticks) {
        this.rideCooldownEnd = GameUtils.getTicksFromGameStart(this.player.level()) + ticks;
        sync();
    }

    /** 清掉冷却（开局/结束时调用）。 */
    public void clearRideCooldown() {
        if (rideCooldownEnd == 0) {
            return;
        }
        rideCooldownEnd = 0;
        sync();
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("rideCdEnd", rideCooldownEnd);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        rideCooldownEnd = tag.getLong("rideCdEnd");
    }
}
