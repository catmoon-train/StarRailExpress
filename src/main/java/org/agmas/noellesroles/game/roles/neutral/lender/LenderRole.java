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

package org.agmas.noellesroles.game.roles.neutral.lender;

import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleTeam;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * 放贷人：中立职业，以合同把金币借给其他玩家并收取利息。
 *
 * <p>
 * 胜利条件：<b>存活到最后，随任意一方获胜</b>——放贷人本身<b>不是独立胜利</b>：
 * 不触发独立结局、不调用 {@code customWinnerWin}、也不阻止游戏结束；
 * 游戏正常结算出任意一方的胜负后（乘客 / 杀手 / 时间 / 亡命徒等），
 * 存活到结算的放贷人随之获胜。
 *
 * <p>
 * 与黑白熊的「依附最近玩家阵营」（6 格内跟随最近存活玩家的胜负）不同：
 * 放贷人<b>不需要待在任何一方身边</b>。结算时由
 * {@code SREMurderGameMode.isPlayerTheWinner} 通过
 * {@link CustomWinnerRoleInterface#didPlayerWin} 逐人判定个人胜负，
 * 本判定只改写放贷人自己的输赢，不影响整体结局。
 */
public class LenderRole extends NormalRole implements CustomWinnerRoleInterface {
    public LenderRole(ResourceLocation identifier, int color, RoleTeam team,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, team, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 玩家是否获胜。在获胜统计时被调用。
     */
    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, GameUtils.WinStatus winStatus) {
        // 非独立胜利：仅结算出胜负后搭车。NONE / NOT_MODIFY（尚未分出胜负）按原判定。
        if (winStatus == GameUtils.WinStatus.NONE || winStatus == GameUtils.WinStatus.NOT_MODIFY) {
            return original;
        }
        // 存活到结算 → 随任意一方获胜
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            return true;
        }
        return original;
    }
}
