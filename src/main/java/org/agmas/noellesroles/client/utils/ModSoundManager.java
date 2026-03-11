package org.agmas.noellesroles.client.utils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

@Environment(EnvType.CLIENT)
public class ModSoundManager {
    public static void setGameSoundLevel(float soundLevel) {
        float settingSoundLevel = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MASTER, settingSoundLevel * soundLevel);
    }

    public static void resetGameSoundLevel() {
        float soundLevel = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
        Minecraft.getInstance().getSoundManager().updateSourceVolume(SoundSource.MASTER, soundLevel);
    }
}
