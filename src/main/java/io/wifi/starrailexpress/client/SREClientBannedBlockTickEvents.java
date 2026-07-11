package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.api.AreasSettings;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent.PlayerBannedBlockTimeInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

public class SREClientBannedBlockTickEvents {
    public static PlayerBannedBlockTimeInfo bannedBlockPlayerInfo = null;
    public static AreasSettings.MapBlockedBlockSetting bannedBlockInfo = null;

    public static void tick(ClientLevel world) {
        if (SREClient.cached_player != null) {
            checkPlayerBannedBlocksClientAndWarns(world, SREClient.cached_player);
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
                || areas.areasSettings.bannedBlock.isEmpty())
            return;
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
                    || info.blockId().equalsIgnoreCase(blockId3)) {

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
