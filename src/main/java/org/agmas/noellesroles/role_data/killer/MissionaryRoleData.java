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

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.exmo.sre.meeting.MeetingManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public class MissionaryRoleData extends SimpleRoleData {

    /** 技能冷却结束时间 (gameTime) */
    private long cooldownEndTick;
    /** 开局冷却标记 */
    private boolean initialCooldownSet;
    /** 当前传教的玩家 UUID，null 表示无目标 */
    private UUID currentTarget;
    /** 对当前目标施加的加成值（用于切换时恢复为原来的权重） */
    private int currentTargetBonus;

    public MissionaryRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer p) { return p == this.player; }

    @Override
    public void init() {
        cooldownEndTick = 0;
        initialCooldownSet = false;
        currentTarget = null;
        currentTargetBonus = 0;
        sync();
    }

    @Override
    public void clear() { init(); }

    /** 检查技能是否可用 */
    public boolean canUse() {
        if (!initialCooldownSet && player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            cooldownEndTick = sl.getGameTime() + 60 * 20L; // 开局60秒冷却
            initialCooldownSet = true;
        }
        return player.level().getGameTime() >= cooldownEndTick;
    }

    /** 返回剩余冷却 tick（用于客户端显示） */
    public long getCooldownRemaining() {
        return Math.max(0, cooldownEndTick - player.level().getGameTime());
    }

    /** 对目标玩家传教，增加其投票权重 */
    public void convert(ServerPlayer target) {
        if (!canUse()) return;
        var game = SREGameWorldComponent.KEY.get(player.level());
        if (game == null) return;

        // 恢复旧目标的权重（减去之前加的加成）
        if (currentTarget != null && currentTargetBonus > 0) {
            MeetingManager.addVoterWeight(currentTarget, -currentTargetBonus);
        }

        // 加算：目标投票权重 +1（存活>24时 +2）
        long alive = target.serverLevel().players().stream()
                .filter(p -> io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(p))
                .count();
        int bonus = alive > 24 ? 2 : 1;
        MeetingManager.addVoterWeight(target.getUUID(), bonus);
        currentTarget = target.getUUID();
        currentTargetBonus = bonus;

        // 进入冷却
        cooldownEndTick = target.serverLevel().getGameTime() + 80 * 20L;
        sync();
    }

    /** 获取当前传教目标的 UUID */
    public UUID getCurrentTarget() { return currentTarget; }



    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("cdEnd", cooldownEndTick);
        tag.putBoolean("initCd", initialCooldownSet);
        if (currentTarget != null) tag.putUUID("tgt", currentTarget);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        cooldownEndTick = tag.getLong("cdEnd");
        initialCooldownSet = tag.getBoolean("initCd");
        if (tag.hasUUID("tgt")) currentTarget = tag.getUUID("tgt");
    }
}
