package org.agmas.noellesroles.game.roles.innocence.attendant;

import io.wifi.starrailexpress.content.block.api.LightBlockInterface;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AttendantHandler {

    public static final int ATTENDANT_LIGHT_DISTANCE = 5;

    public static void openLight(ServerPlayer player) {
        openLight(player, player.position(), ATTENDANT_LIGHT_DISTANCE);
    }

    public static void openLight(ServerPlayer player, Vec3 startPos, int distance) {
        int lightCount = 0;
        if (!(player.level() instanceof ServerLevel level))
            return;
        for (var p : player.getServer().getPlayerList().getPlayers()) {
            if (p.distanceToSqr(startPos) <= 8 * 8) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        TMMSounds.BLOCK_LIGHT_TOGGLE,
                        SoundSource.BLOCKS, 1F, 1F);
            }
        }

        int pX = (int) Math.round(startPos.x());
        int pY = (int) Math.round(startPos.y());
        int pZ = (int) Math.round(startPos.z());
        int minX = pX - distance;
        int minY = pY - distance;
        int minZ = pZ - distance;
        int maxX = pX + distance;
        int maxY = pY + distance;
        int maxZ = pZ + distance;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var blockState = level.getBlockState(pos);
                    boolean isDirty = false;
                    if (blockState.getBlock() instanceof LightBlockInterface) {
                        if (!blockState.getOptionalValue(LightBlockInterface.ACTIVE).isEmpty()) {
                            blockState = (BlockState) blockState.setValue(LightBlockInterface.ACTIVE, true);
                        }
                        if (!blockState.getOptionalValue(LightBlockInterface.LIT).isEmpty()) {
                            blockState = (BlockState) blockState.setValue(LightBlockInterface.LIT, true);
                        }
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
