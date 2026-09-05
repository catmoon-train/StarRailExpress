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

package org.agmas.noellesroles.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 耳聋效果：效果刚加上时立刻停掉已在播放的声音。
 * 新声音由 {@code DeafnessSoundMixin} 拦截，音量由 {@code SoundVolumeMixin} 归零。
 */
@Environment(EnvType.CLIENT)
public final class DeafnessClientHandle {
    public static boolean active;

    private DeafnessClientHandle() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DeafnessClientHandle::tick);
    }

    public static boolean isLocalPlayerDeaf() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(ModEffects.DEAFNESS);
    }

    private static void tick(Minecraft client) {
        boolean now = isLocalPlayerDeaf();
        if (now && !active && client.getSoundManager() != null) {
            client.getSoundManager().stop();
        }
        active = now;
    }
}
