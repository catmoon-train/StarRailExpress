package org.agmas.noellesroles;

import io.wifi.starrailexpress.block.NeonPillarBlock;
import io.wifi.starrailexpress.block.NeonTubeBlock;
import io.wifi.starrailexpress.block.ToggleableFacingLightBlock;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

public class AttendantHandler {

    public static int area_distance = 5;

    public static void openLight(ServerPlayer player) {
        int lightCount = 0;
        if (!(player.level() instanceof ServerLevel level))
            return;
        int pY = (int) Math.round(player.getY());
        for (var p : player.getServer().getPlayerList().getPlayers()) {
            if (p.distanceTo(player) <= 8) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        TMMSounds.BLOCK_LIGHT_TOGGLE,
                        SoundSource.BLOCKS, 1F, 1F);
            }

        }

        int pX = (int) Math.round(player.getX());
        int pZ = (int) Math.round(player.getZ());
        int minX = pX - area_distance;
        int minY = pY - area_distance;
        int minZ = pZ - area_distance;
        int maxX = pX + area_distance;
        int maxY = pY + area_distance;
        int maxZ = pZ + area_distance;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var blockState = level.getBlockState(pos);
                    boolean isDirty = false;
                    if (blockState.getBlock() instanceof NeonPillarBlock) {
                        blockState = (BlockState) blockState.setValue(NeonPillarBlock.ACTIVE, true);
                        blockState = (BlockState) blockState.setValue(NeonPillarBlock.LIT, true);
                        isDirty = true;
                    } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                        blockState = (BlockState) blockState.setValue(NeonTubeBlock.ACTIVE, true);
                        blockState = (BlockState) blockState.setValue(NeonTubeBlock.LIT, true);
                        isDirty = true;
                    } else if (blockState.getBlock() instanceof NeonTubeBlock) {
                        blockState = (BlockState) blockState.setValue(NeonTubeBlock.ACTIVE, true);
                        blockState = (BlockState) blockState.setValue(NeonTubeBlock.LIT, true);
                        isDirty = true;
                    } else if (blockState.getBlock() instanceof ToggleableFacingLightBlock) {
                        blockState = (BlockState) blockState.setValue(ToggleableFacingLightBlock.ACTIVE, true);
                        blockState = (BlockState) blockState.setValue(ToggleableFacingLightBlock.LIT, true);
                        isDirty = true;
                    }
                    if (isDirty) {
                        level.setBlockAndUpdate(pos, blockState);
                        lightCount++;
                    }
                }
            }
        }
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.attendant.active", lightCount).withStyle(ChatFormatting.GOLD), true);
    }

}
