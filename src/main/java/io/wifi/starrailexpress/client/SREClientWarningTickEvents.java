package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.PlayerBannedBlockTimeInfo;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;

public class SREClientWarningTickEvents {
    public static PlayerBannedBlockTimeInfo bannedBlockPlayerInfo = null;
    public static AreasSettings.MapBlockedBlockSetting bannedBlockInfo = null;
    public static int darknessTime = 0;

    public static void tick(ClientLevel world) {
        if (SREClient.areaComponent == null)
            return;
        if (SREClient.cached_player != null) {
            checkPlayerBannedBlocksClientAndWarns(world, SREClient.cached_player);
            checkPlayerDarkness(world, SREClient.cached_player);
        }
    }

    private static void checkPlayerDarkness(ClientLevel level, Player player) {
        int limit = SREClient.areaComponent.areasSettings.deadInDarknessTime;
        if (player.isSpectator() || player.isCreative() || limit <= 0) {
            darknessTime = 0;
            return;
        }
        var role = SREClient.getCachedPlayerRole();
        if (role == null) {
            darknessTime = 0;
            return;
        }
        if (role.isKillerTeam()) {
            darknessTime = 0;
            return;
        }
        if (SREWorldBlackoutComponent.KEY.get(level).isBlackoutActive())
            return;
        if (level.getBrightness(LightLayer.BLOCK, BlockPos.containing(player.getEyePosition())) < 3
                && (level.getBrightness(LightLayer.SKY,
                        BlockPos.containing(player.getEyePosition())) < 10
                        || level.getDayTime() > 13000)) {
            darknessTime++;
        } else {
            darknessTime = 0;
        }
    }

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
            return;
        }

        final var pos1 = SREGameWorldComponent.findNearestSupportBelow(player, 3);
        if (pos1 == null)
            return;
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
