package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.PlayerBannedBlockTimeInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
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
        final var pos1 = player.blockPosition();
        final var pos2 = pos1.below();
        final var pos3 = pos2.below();
        final var blockState1 = level.getBlockState(pos1);
        final var blockState2 = level.getBlockState(pos2);
        final var blockState3 = level.getBlockState(pos3);
        final String blockId1 = SREGameWorldComponent.getBlockId(blockState1);
        final String blockId2 = SREGameWorldComponent.getBlockId(blockState2);
        final String blockId3 = SREGameWorldComponent.getBlockId(blockState3);

        for (var info : areas.areasSettings.bannedBlock) {
            if (info.blockId() == null)
                continue;
            if (info.blockId().equalsIgnoreCase(blockId1) || info.blockId().equalsIgnoreCase(blockId2)
                    || (blockState2.is(BlockTags.AIR) && info.blockId().equalsIgnoreCase(blockId3))) {

                bannedBlockInfo = info;
                if (bannedBlockPlayerInfo == null || bannedBlockPlayerInfo.standonTick <= 0) {
                    bannedBlockPlayerInfo = new PlayerBannedBlockTimeInfo(info.blockId(), level.getGameTime());
                } else if (!bannedBlockPlayerInfo.blockId.equalsIgnoreCase(info.blockId())) {
                    bannedBlockPlayerInfo = (new PlayerBannedBlockTimeInfo(info.blockId(), level.getGameTime()));
                }
                return;
            }
        }
        bannedBlockPlayerInfo = null;
        bannedBlockInfo = null;
    }
}
