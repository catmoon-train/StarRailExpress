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

package org.agmas.noellesroles.game.roles.neutral.leader;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.api.CustomWinnerRoleInterface;
import io.wifi.starrailexpress.api.EggRoleInterface;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import org.agmas.noellesroles.role_data.neutral.LeaderRoleData;

/**
 * 领袖（Leader）职业。
 *
 * <ul>
 * <li>中立阵营（NormalRole 构造自动 {@code setNeutrals(true)}，不调用
 * {@code setNeutralForKiller}）</li>
 * <li>无限体力、假心情、可见计分板、可被赌徒等随机到 {@code false}</li>
 * <li>仅 18 人及以上对局生成</li>
 * <li>胜利判定：随追随者胜利（仅依附，不独立宣布）</li>
 * </ul>
 */
public class LeaderRole extends CustomWinnerRole implements EggRoleInterface {

    public LeaderRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 领袖胜利判定：仅依附追随者，不独立宣布。
     * <p>
     * 任一追随者在本局被判定为胜利，且领袖仍未获胜时，领袖即随其获胜（无论领袖是否存活）。
     */
    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        LeaderRoleData data = RoleData.getNullable(LeaderRoleData.class, player);
        if (data == null || data.followers.isEmpty()) {
            return original;
        }
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
        SREGameRoundEndComponent roundEnd = SREGameRoundEndComponent.KEY.get(player.level());
        for (int i = 0; i < data.followers.size(); i++) {
            UUID followerId = data.followers.get(i);
            String originalRolePath = i < data.followerRoleIds.size() ? data.followerRoleIds.get(i) : "";
            ServerPlayer follower = player.serverLevel().getServer().getPlayerList().getPlayer(followerId);
            if (follower == null) {
                continue;
            }
            SRERole fr = game.getRole(follower);
            if (fr == null) {
                continue;
            }
            // 初学者追随者一旦转职（不再是初学者），领袖不再随其获胜
            if (originalRolePath.equals("initiate") && !fr.identifier().getPath().equals("initiate")) {
                continue;
            }
            // 森近霖之助 / 河城荷取：金币依附（任意胜利，非独立）
            if (LeaderFollowerEffects.isCoinDependentRole(fr)) {
                if (coinDependentWin(player, follower, game, winStatus)) {
                    return true;
                }
                continue;
            }
            if (followerWon(follower, fr, winStatus, roundEnd)) {
                return true;
            }
        }
        return original;
    }

    /**
     * 森近霖之助 / 河城荷取金币依附：总金币达到 总人数×80 且本局有人胜利即算胜（非独立）。
     */
    private boolean coinDependentWin(ServerPlayer leader, ServerPlayer follower,
            SREGameWorldComponent game, WinStatus winStatus) {
        // 本局无人获胜则不依附
        if (winStatus == WinStatus.NONE || winStatus == WinStatus.NOT_MODIFY
                || winStatus == WinStatus.NO_PLAYER) {
            return false;
        }
        int totalCoins = SREPlayerShopComponent.KEY.get(leader).balance
                + SREPlayerShopComponent.KEY.get(follower).balance;
        int playerCount = Math.max(1, game.getPlayerCount());
        return totalCoins >= playerCount * 80;
    }

    /**
     * 判断追随者是否在本局获胜（复用与 recordWinStats 一致的判定逻辑）。
     */
    private boolean followerWon(ServerPlayer follower, SRERole fr, WinStatus winStatus,
            SREGameRoundEndComponent roundEnd) {
        // 自定义胜利职业（黑白等）用其自身判定
        if (fr instanceof CustomWinnerRoleInterface cwr) {
            if (cwr.didPlayerWin(follower, false, winStatus)) {
                return true;
            }
        }
        switch (winStatus) {
            case CUSTOM:
            case CUSTOM_COMPONENT:
                if (roundEnd.CustomWinnerPlayers.contains(follower.getUUID())) {
                    return true;
                }
                if (roundEnd.CustomWinnerID != null
                        && roundEnd.CustomWinnerID.equals(fr.identifier().getPath())) {
                    return true;
                }
                return roundEnd.CustomWinnerExtraRoleIds != null
                        && roundEnd.CustomWinnerExtraRoleIds.contains(fr.identifier().getPath());
            case GAMBLER:
                return fr.identifier().getPath().equals("gambler");
            case KILLERS:
                return fr.winWithKiller();
            case LOOSE_END:
                return fr.identifier().equals(TMMRoles.LOOSE_END.identifier());
            case NIAN_SHOU:
                return fr.identifier().getPath().equals("nianshou");
            case LOVERS:
                return roundEnd.CustomWinnerPlayers.contains(follower.getUUID());
            case TIME:
            case PASSENGERS:
                return fr.winWithInnocent();
            case RECORDER:
                return fr.identifier().getPath().equals("recorder");
            default:
                return false;
        }
    }
}
