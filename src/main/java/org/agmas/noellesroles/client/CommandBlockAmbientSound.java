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

import io.wifi.starrailexpress.client.util.MyBackgroundAmbientLoop;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.NRSounds;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 命令方块事件的环境音效：按破坏阶段给全场循环播放不同的背景音，音量较小。
 * <ul>
 * <li>阶段 0（完好）：{@link NRSounds#COMMAND_BLOCK}</li>
 * <li>阶段 1（裂纹 1/3~2/3）：{@link NRSounds#COMMAND_BLOCK_2}</li>
 * <li>阶段 2（濒临摧毁）：{@link NRSounds#COMMAND_BLOCK_3}</li>
 * </ul>
 * 阶段跨越时旧音效淡出、新音效淡入；阶段 3（已摧毁）或对局结束后淡出停止。
 *
 * <p>做法对齐 {@code OutsideSoundManager}：用 {@link MyBackgroundAmbientLoop} 的播放谓词控制启停，
 * 谓词归假即淡出，无需在对局结束处额外手动打断。音效挂在本地玩家身上、AMBIENT 声源、
 * relative 定位，所有玩家无论距命令方块多远都能以相同小音量听到。</p>
 */
public final class CommandBlockAmbientSound {
    /** 环境音效音量（较小，背景音） */
    private static final float VOLUME = 0.25F;
    private static final int FADE_IN = 20;
    private static final int FADE_OUT = 20;
    private static final AtomicReference<SoundInstance> CURRENT = new AtomicReference<>();
    /** 当前循环音效对应的破坏阶段；-1 表示尚未播放。 */
    private static int stage = -1;

    private CommandBlockAmbientSound() {
    }

    public static void register() {
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            Minecraft client = Minecraft.getInstance();
            SoundManager soundManager = client.getSoundManager();
            if (soundManager == null) {
                return;
            }
            if (!shouldPlay(client)) {
                stop(soundManager);
                return;
            }
            int next = CommandBlockProgressClient.stage();
            if (next >= 3) {
                // 阶段 3 已被摧毁，对局随即结束，直接淡出
                stop(soundManager);
                return;
            }
            SoundInstance sound = CURRENT.get();
            boolean stale = sound == null || !soundManager.isActive(sound)
                    || (sound instanceof MyBackgroundAmbientLoop loop && loop.isStopped());
            if (next != stage || stale) {
                LocalPlayer player = client.player;
                if (player == null) {
                    return;
                }
                if (!stale) {
                    // 阶段跨越：旧音效淡出，与新音效交叉淡入
                    stop(soundManager);
                }
                SoundInstance nextSound = new MyBackgroundAmbientLoop(
                        player,
                        soundOf(next),
                        SoundSource.AMBIENT,
                        VOLUME,
                        CommandBlockAmbientSound::alive,
                        FADE_IN, FADE_OUT);
                CURRENT.set(nextSound);
                stage = next;
                soundManager.play(nextSound);
            }
        });
    }

    /** 本局是否为命令方块事件：进度条可见即代表事件进行中，对局结束后由服务端清零而归假。 */
    private static boolean shouldPlay(Minecraft client) {
        return client.player != null && CommandBlockProgressClient.isShowing();
    }

    private static SoundEvent soundOf(int stage) {
        return switch (stage) {
            case 1 -> NRSounds.COMMAND_BLOCK_2;
            case 2 -> NRSounds.COMMAND_BLOCK_3;
            default -> NRSounds.COMMAND_BLOCK;
        };
    }

    private static boolean alive(LocalPlayer player) {
        return player != null && CommandBlockProgressClient.isShowing();
    }

    private static void stop(SoundManager soundManager) {
        SoundInstance old = CURRENT.getAndSet(null);
        if (old instanceof MyBackgroundAmbientLoop loop) {
            loop.tryStop();
        } else if (old != null && soundManager.isActive(old)) {
            soundManager.stop(old);
        }
        stage = -1;
    }
}
