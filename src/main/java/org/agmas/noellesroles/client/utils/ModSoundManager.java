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
