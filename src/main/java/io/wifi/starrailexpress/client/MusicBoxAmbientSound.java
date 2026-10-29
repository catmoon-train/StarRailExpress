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

package io.wifi.starrailexpress.client;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.agmas.noellesroles.client.CommandBlockProgressClient;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbientLoop;
import io.wifi.starrailexpress.util.ItemSkinManager;
import io.wifi.starrailexpress.util.MusicBoxTracks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 音乐盒背景音乐：玩家装配了带曲目的音乐盒时，在对局中低音量循环播放它。
 * <p>
 * 播放条件（每次 tick 重新判定，任一不满足即淡出停止）：
 * <ul>
 * <li>对局进行中（状态为 {@code INITIATING} / {@code ACTIVE}，进入结算即停声）；</li>
 * <li>当局不是命令方块事件局（{@link CommandBlockProgressClient#isShowing()} 为 false，
 * 该局背景音由 {@code CommandBlockAmbientSound} 负责）；</li>
 * <li>玩家当前装配的 {@link ItemSkinManager.SkinTypes#MUSIC_BOX} 皮肤在 {@link MusicBoxTracks} 里登记过曲目。</li>
 * </ul>
 *
 * <p>音乐盒优先于地图自带背景音：音乐盒一旦开始播放，{@code OutsideSoundManager} 会让位静音
 * （见 {@link #overridesSceneSound()}），地图背景音淡出、卸下音乐盒后自动恢复。
 * 命令方块事件局例外——那一局由事件音乐负责，音乐盒不播、地图背景音保持原样。</p>
 *
 * <p>结算音效不走本类的循环播放，而是由 {@code RoundTextRenderer} 通过
 * {@link #equippedSettlementSound(boolean)} 取用：音乐盒登记了结算成功 / 失败音效就替换默认钢琴音，
 * 否则（含没装配音乐盒）走默认结算音效。</p>
 *
 * <p>做法与 {@code CommandBlockAmbientSound} / {@code OutsideSoundManager} 一致：用
 * {@link MyBackgroundAmbientLoop} 的播放谓词控制启停，音效挂在本地玩家身上、AMBIENT 声源、
 * relative 定位，因此无论玩家在地图哪里都以同样的小音量听到。曲线切换时旧曲淡出、新曲淡入。</p>
 */
public final class MusicBoxAmbientSound {
    /** 音乐盒音量：比场景背景音更小，只作陪衬 */
    private static final float VOLUME = 0.2F;
    private static final int FADE_IN = 40;
    private static final int FADE_OUT = 20;

    private static final AtomicReference<SoundInstance> CURRENT = new AtomicReference<>();
    /** 当前正在播放的音乐盒皮肤 ID；{@code null} 表示没有在播放。 */
    private static String playingSkin;

    private MusicBoxAmbientSound() {
    }

    public static void register() {
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            Minecraft client = Minecraft.getInstance();
            SoundManager soundManager = client.getSoundManager();
            if (soundManager == null) {
                return;
            }
            String skin = playingMusicBox(client);
            ResourceLocation soundId = skin == null ? null : MusicBoxTracks.getSoundId(skin);
            if (soundId == null) {
                stop(soundManager);
                return;
            }
            SoundInstance sound = CURRENT.get();
            boolean stale = sound == null || !soundManager.isActive(sound)
                    || (sound instanceof MyBackgroundAmbientLoop loop && loop.isStopped());
            if (stale || !Objects.equals(skin, playingSkin)) {
                LocalPlayer player = client.player;
                if (player == null) {
                    return;
                }
                stop(soundManager);
                SoundInstance next = new MyBackgroundAmbientLoop(
                        player,
                        SoundEvent.createVariableRangeEvent(soundId),
                        SoundSource.AMBIENT,
                        VOLUME,
                        MusicBoxAmbientSound::alive,
                        FADE_IN, FADE_OUT);
                CURRENT.set(next);
                playingSkin = skin;
                soundManager.play(next);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CURRENT.set(null);
            playingSkin = null;
        });
    }

    /**
     * 当前是否由音乐盒接管场景背景音。
     * <p>装配了带对局音效的音乐盒、且对局正在进行（非命令方块事件局）时为 {@code true}，
     * 此时地图自带的背景音（{@code OutsideSoundManager}）应静音让位。</p>
     */
    public static boolean overridesSceneSound() {
        return playingMusicBox(Minecraft.getInstance()) != null;
    }

    /**
     * 玩家装配的音乐盒在对局结算时该放的音效（替换默认的结算钢琴音）。
     * <p>只依赖「自己装配的音乐盒」：没装配音乐盒、或该音乐盒没有登记对应结算音效时返回
     * {@code null}，调用方据此回落到默认结算音效。</p>
     *
     * @param win 本局是否胜利
     * @return 结算音效；没有可替换的音效时返回 {@code null}
     */
    public static SoundEvent equippedSettlementSound(boolean win) {
        Minecraft client = Minecraft.getInstance();
        String skin = equippedMusicBox(client);
        if (skin == null || "default".equalsIgnoreCase(skin)) {
            return null;
        }
        ResourceLocation soundId = win
                ? MusicBoxTracks.getWinSound(skin)
                : MusicBoxTracks.getLoseSound(skin);
        return soundId == null ? null : SoundEvent.createVariableRangeEvent(soundId);
    }

    /** 当前该播放哪首曲目对应的音乐盒皮肤；返回 {@code null} 表示不该播放。 */
    private static String playingMusicBox(Minecraft client) {
        if (!shouldPlay(client)) {
            return null;
        }
        String skin = equippedMusicBox(client);
        if (skin == null || "default".equalsIgnoreCase(skin) || !MusicBoxTracks.hasTrack(skin)) {
            return null;
        }
        return skin.toLowerCase(Locale.ROOT);
    }

    /** 播放条件：对局进行中、非命令方块事件局（地图自带背景音会让位给音乐盒）。 */
    private static boolean shouldPlay(Minecraft client) {
        if (client.player == null || client.level == null) {
            return false;
        }
        if (!isRoundInProgress()) {
            return false;
        }
        if (SREClient.areaComponent == null || SREClient.areaComponent.areasSettings == null) {
            return false;
        }
        // 命令方块事件局有自己的环境音，音乐盒让位
        return !CommandBlockProgressClient.isShowing();
    }

    /**
     * 对局是否处于「开始游戏后 ~ 游戏结束前」。
     * <p>{@code STOPPING} 已是结算阶段，按需求这时就该停止播放，所以不把
     * {@link SREClient#isGameRunning()}（含 STOPPING）直接拿来用。</p>
     */
    private static boolean isRoundInProgress() {
        SREGameWorldComponent game = SREClient.gameComponent;
        if (game == null) {
            return false;
        }
        SREGameWorldComponent.GameStatus status = game.getGameStatus();
        return status == SREGameWorldComponent.GameStatus.INITIATING
                || status == SREGameWorldComponent.GameStatus.ACTIVE;
    }

    private static String equippedMusicBox(Minecraft client) {
        if (client.player == null) {
            return null;
        }
        try {
            SREPlayerSkinsComponent skins = SREPlayerSkinsComponent.KEY.get(client.player);
            return skins.getEquippedSkin(ItemSkinManager.SkinTypes.MUSIC_BOX);
        } catch (RuntimeException ignored) {
            // 组件尚未就绪（如对局刚开始）
            return null;
        }
    }

    private static boolean alive(LocalPlayer player) {
        return player != null && shouldPlay(Minecraft.getInstance());
    }

    private static void stop(SoundManager soundManager) {
        SoundInstance old = CURRENT.getAndSet(null);
        if (old instanceof MyBackgroundAmbientLoop loop) {
            loop.tryStop();
        } else if (old != null && soundManager.isActive(old)) {
            soundManager.stop(old);
        }
        playingSkin = null;
    }
}
