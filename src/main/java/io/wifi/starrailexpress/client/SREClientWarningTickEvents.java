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

package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.PlayerBannedBlockTimeInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

public class SREClientWarningTickEvents {
    public static PlayerBannedBlockTimeInfo bannedBlockPlayerInfo = null;
    public static AreasSettings.MapBlockedBlockSetting bannedBlockInfo = null;
    public static int darknessTime = 0;

    public static void tick(ClientLevel world) {
        if (SREClient.areaComponent == null)
            return;
        if (SREClient.cached_player != null) {
            checkPlayerBannedBlocksClientAndWarns(world, SREClient.cached_player);
        }
    }

    // private static void checkPlayerDarkness(ClientLevel level, Player player) {
    //     int limit = SREClient.areaComponent.areasSettings.deadInDarknessTime;
    //     if (player.isSpectator() || player.isCreative() || limit <= 0) {
    //         darknessTime = 0;
    //         return;
    //     }
    //     var role = SREClient.getCachedPlayerRole();
    //     if (role == null) {
    //         darknessTime = 0;
    //         return;
    //     }
    //     if (role.isKillerTeam()) {
    //         darknessTime = 0;
    //         return;
    //     }
    //     if (SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive())
    //         return;
    //     if (level.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) < 3
    //             && (level.getBrightness(LightLayer.SKY,
    //                     BlockPos.containing(player.getEyePosition())) < 10
    //                     || level.getDayTime() > 13000)) {
    //         darknessTime++;
    //     } else {
    //         darknessTime = 0;
    //     }
    // }

    private static void checkPlayerBannedBlocksClientAndWarns(ClientLevel level, Player player) {
        if (level.getGameTime() % 2 != 0) // 2tick 检测一次
            return;
        if (player.isSpectator() || player.isCreative()) {
            bannedBlockPlayerInfo = null;
            bannedBlockInfo = null;
            return;
        }
        final var areas = SREClient.areaComponent;
        if (areas == null || areas.areasSettings == null || areas.areasSettings.bannedBlock == null
                || areas.areasSettings.bannedBlock.isEmpty()) {
            bannedBlockPlayerInfo = null;
            bannedBlockInfo = null;
            return;
        }
        var role = SREClient.getCachedPlayerRole();
        if (role == null) {
            bannedBlockPlayerInfo = null;
            bannedBlockInfo = null;
            return;
        }

        final var pos1 = SREGameWorldComponent.findNearestSupportBelow(player, 3);
        if (pos1 == null){
            bannedBlockPlayerInfo = null;
            bannedBlockInfo = null;
            return;
        }
        final var blockState1 = level.getBlockState(pos1);
        final String blockId1 = SREGameWorldComponent.getBlockId(blockState1);

        for (var info : areas.areasSettings.bannedBlock) {
            if (info.blockId() == null)
                continue;
            if (info.blockId().equalsIgnoreCase(blockId1)) {
                boolean canDead = true;
                if (SREGameWorldComponent.isKillerTeamRoleStatic(role)) {
                    if (info.deathTimeForKillers() < 0) {
                        canDead = false;
                    }
                } else {
                    if (info.deathTimeForInnocent() < 0) {
                        canDead = false;
                    }
                }
                if (canDead) {
                    bannedBlockInfo = info;
                    if (bannedBlockPlayerInfo == null || bannedBlockPlayerInfo.standonTick <= 0) {
                        bannedBlockPlayerInfo = new PlayerBannedBlockTimeInfo(info.blockId(), level.getGameTime());
                    } else if (!bannedBlockPlayerInfo.blockId.equalsIgnoreCase(info.blockId())) {
                        bannedBlockPlayerInfo = (new PlayerBannedBlockTimeInfo(info.blockId(), level.getGameTime()));
                    }
                    return;
                }
            }
        }
        bannedBlockPlayerInfo = null;
        bannedBlockInfo = null;
    }
}
