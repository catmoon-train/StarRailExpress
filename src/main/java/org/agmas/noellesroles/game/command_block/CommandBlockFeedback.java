package org.agmas.noellesroles.game.command_block;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** 命令方块道具的使用音效。右键生效时至少要有一声反馈。 */
public final class CommandBlockFeedback {
    private CommandBlockFeedback() {
    }

    public static void play(Level level, Entity at, SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide || at == null || sound == null) {
            return;
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
