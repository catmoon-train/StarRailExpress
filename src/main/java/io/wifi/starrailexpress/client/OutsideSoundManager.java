package io.wifi.starrailexpress.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.api.AreasSettings.BackgroundAmbienceSound;
import io.wifi.starrailexpress.client.util.MyBackgroundAmbientLoop;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.agmas.noellesroles.init.NRSounds;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OutsideSoundManager {
    public static final AtomicReference<SoundInstance> playingSounds = new AtomicReference<>();
    public static final AtomicBoolean isNowPlayingInside = new AtomicBoolean();
    public static final CopyOnWriteArrayList<SoundInstance> PENDING_STOP = new CopyOnWriteArrayList<>();

    public static void registerEvents() {

        // 延迟停止音效（支持淡出）
        ClientTickEvents.END_CLIENT_TICK.register(c -> {

            SoundManager soundManager = c.getSoundManager();
            if (PENDING_STOP.isEmpty())
                return;

            if (soundManager == null) {
                PENDING_STOP.clear();
                return;
            }
            if (c.player == null || c.level == null) {
                // 世界未加载时强制停止所有
                for (SoundInstance sound : PENDING_STOP) {
                    if (soundManager.isActive(sound))
                        soundManager.stop(sound);
                }
                PENDING_STOP.clear();
            } else {
                Iterator<SoundInstance> it = PENDING_STOP.iterator();
                while (it.hasNext()) {
                    SoundInstance sound = it.next();
                    boolean canStop = !(sound instanceof MyBackgroundAmbientLoop loop) || loop.isStopped();
                    if (canStop) {
                        if (soundManager.isActive(sound))
                            soundManager.stop(sound);
                        it.remove();
                    }
                }
            }
        });

        // 每世界 tick 检查并切换内外音效
        ClientTickEvents.START_WORLD_TICK.register(world -> {

            Minecraft client = Minecraft.getInstance();
            SoundManager soundManager = client.getSoundManager();
            if (soundManager == null) {
                return;
            }
            if (!shouldPlaySound(client)) {
                stopNowPlayingSounds(soundManager);
                return;
            }

            boolean inside = isInside(client);
            boolean currentlyInside = isNowPlayingInside.get();
            SoundInstance current = playingSounds.get();

            if (current == null || inside != currentlyInside) {
                if (inside) {
                    playInsideSound(client, soundManager);
                } else {
                    playOutsideSound(client, soundManager);
                }
            }
        });
    }

    // ---------- 播放控制 ----------

    /** 停止当前正在播放的音效（加入延迟停止队列） */
    public static void stopNowPlayingSounds(SoundManager soundManager) {
        SoundInstance old = playingSounds.getAndSet(null);
        if (old != null && soundManager.isActive(old)) {
            PENDING_STOP.add(old);
        }
    }

    /** 安全切换音效：停止旧音效并立即播放新音效 */
    public static void stopAndPlayNew(SoundManager soundManager, SoundInstance newSound) {
        SoundInstance old = playingSounds.get();
        // 避免重复播放相同音效
        if (newSound.equals(old))
            return;

        if (old != null && soundManager.isActive(old)) {
            PENDING_STOP.add(old);
        }
        playingSounds.set(newSound);
        soundManager.play(newSound);
    }

    // ---------- 条件判断 ----------

    public static boolean shouldPlaySound(Minecraft client) {
        return SREClient.gameComponent != null
                && SREClient.areaComponent != null
                && SREClient.areaComponent.areasSettings != null
                && SREClient.areaComponent.areasSettings.haveOutsideSound
                && client.player != null
                && (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(client.player)
                        || SREClientConfig.instance().bgsoundForSpectator);
    }

    public static boolean isInside(Minecraft client) {
        return client.player != null
                && SREClient.gameComponent != null
                && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(client.player)
                && SREClient.isGameRunning()
                && !SRE.isSkyVisible(client.player);
    }

    /** 用于 MyBackgroundAmbientLoop 的存活判断，防止状态变化后音效仍在播放 */
    public static boolean shouldPlayPredicate(boolean isInside) {
        Minecraft client = Minecraft.getInstance();
        return shouldPlaySound(client) && (isInside(client) == isInside);
    }

    // ---------- 具体播放逻辑 ----------

    private static void playOutsideSound(Minecraft client, SoundManager soundManager) {
        ResourceLocation loc = getSoundLocation(
                SREClient.areaComponent.areasSettings.sceneOutsideSound,
                SREClient.areaComponent.areasSettings.customOutsideSoundId,
                false);
        if (loc == null) {
            stopNowPlayingSounds(soundManager);
            return;
        }

        // 室外音量默认 1.0，若无专门字段可替换为 areasSettings.outdoorSoundVolume
        float volume = clampVolume(1.0f);
        SoundInstance instance = new MyBackgroundAmbientLoop(
                client.player,
                SoundEvent.createVariableRangeEvent(loc),
                SoundSource.MASTER,
                volume,
                t -> shouldPlayPredicate(false),
                20, 10);
        stopAndPlayNew(soundManager, instance);
        isNowPlayingInside.set(false);
    }

    private static void playInsideSound(Minecraft client, SoundManager soundManager) {
        ResourceLocation loc = getSoundLocation(
                SREClient.areaComponent.areasSettings.sceneOutsideSound,
                SREClient.areaComponent.areasSettings.customOutsideSoundId,
                true);
        if (loc == null) {
            stopNowPlayingSounds(soundManager);
            return;
        }

        float volume = clampVolume(SREClient.areaComponent.areasSettings.indoorSoundVolume);
        SoundInstance instance = new MyBackgroundAmbientLoop(
                client.player,
                SoundEvent.createVariableRangeEvent(loc),
                SoundSource.MASTER,
                volume,
                t -> shouldPlayPredicate(true),
                20, 10);
        stopAndPlayNew(soundManager, instance);
        isNowPlayingInside.set(true);
    }

    // ---------- 工具方法 ----------

    /** 限制音量在 [0, 2] 范围内 */
    private static float clampVolume(float volume) {
        if (volume < 0)
            return 0;
        if (volume > 2)
            return 2;
        return volume;
    }

    /** 根据场景类型和位置获取音效资源路径 */
    public static ResourceLocation getSoundLocation(BackgroundAmbienceSound soundType, String customSoundId,
            boolean isIndoor) {
        return switch (soundType) {
            case circus -> (isIndoor ? NRSounds.CIRCUS_INDOOR : NRSounds.CIRCUS_BACKGROUND).getLocation();
            case custom -> ResourceLocation.tryParse(customSoundId);
            case flower_sea -> NRSounds.FLOWER_OUTDOOR.getLocation();
            case indoor_music -> NRSounds.MUSIC_INDOOR.getLocation();
            case sakura_moyu -> NRSounds.MUSIC_SAKURA_MOYU.getLocation();
            case sand_storm -> NRSounds.SAND_STORM.getLocation();
            case snow_storm -> NRSounds.SNOW_STORM.getLocation();
            case train -> (isIndoor ? TMMSounds.AMBIENT_TRAIN_INSIDE : TMMSounds.AMBIENT_TRAIN_OUTSIDE).getLocation();
            case unwelcome_school -> NRSounds.MUSIC_UNWELCOME_SCHOOL.getLocation();
            case wind -> NRSounds.WIND.getLocation();
            case zenrianbanka -> NRSounds.MUSIC_ZENRIANBANKA.getLocation();
            default -> null;
        };
    }
}