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

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class OutsideSoundManager {
    public static final AtomicReference<SoundInstance> playingSounds = new AtomicReference<>();
    public static final AtomicBoolean isNowPlayingInside = new AtomicBoolean();
    public static final CopyOnWriteArrayList<SoundInstance> PENDING_STOP = new CopyOnWriteArrayList<>();

    public static void registerEvents() {
        // 清理已停止的音效（安全兜底）
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            SoundManager soundManager = c.getSoundManager();
            if (PENDING_STOP.isEmpty())
                return;
            if (soundManager == null) {
                PENDING_STOP.clear();
                return;
            }
            if (c.player == null || c.level == null) {
                for (SoundInstance sound : PENDING_STOP) {
                    if (soundManager.isActive(sound))
                        soundManager.stop(sound);
                }
                PENDING_STOP.clear();
            } else {
                PENDING_STOP.removeIf(sound -> {
                    boolean canStop = !(sound instanceof MyBackgroundAmbientLoop loop) || loop.isStopped();
                    if (canStop) {
                        if (soundManager.isActive(sound))
                            soundManager.stop(sound);
                        return true;
                    }
                    return false;
                });
            }
        });

        // 主循环：内外切换
        ClientTickEvents.START_WORLD_TICK.register(world -> {
            Minecraft client = Minecraft.getInstance();
            SoundManager soundManager = client.getSoundManager();
            if (soundManager == null)
                return;

            if (!shouldPlaySound(client)) {
                stopAllSounds(soundManager);
                return;
            }

            boolean inside = isInside(client);
            boolean currentlyInside = isNowPlayingInside.get();
            SoundInstance current = playingSounds.get();

            // 需要新建音效的情形：无音效、内外变化、音效已结束（或被意外停止）
            if (current == null || inside != currentlyInside
                    || !soundManager.isActive(current)
                    || (current instanceof MyBackgroundAmbientLoop loop && loop.isStopped())) {
                if (inside) {
                    playInsideSound(client, soundManager);
                } else {
                    playOutsideSound(client, soundManager);
                }
            }
        });
    }

    // ---------- 播放控制 ----------

    /** 停止所有背景音效（通过 tryStop 触发淡出） */
    private static void stopAllSounds(SoundManager soundManager) {
        SoundInstance old = playingSounds.getAndSet(null);
        if (old instanceof MyBackgroundAmbientLoop loop) {
            loop.tryStop();
        }
        // 淡出完成后会自动停止，或由 END_CLIENT_TICK 清理
    }

    /** 切换音效：旧音效淡出，新音效立刻播放 */
    private static void switchToNewSound(SoundManager soundManager, SoundInstance newSound) {
        SoundInstance old = playingSounds.getAndSet(newSound);
        if (old instanceof MyBackgroundAmbientLoop loop) {
            loop.tryStop(); // 旧音效开始淡出，不会立即停止
        }
        soundManager.play(newSound);
    }

    // ---------- 条件判断 ----------

    public static boolean shouldPlaySound(Minecraft client) {
        return SREClient.gameComponent != null
                && SREClient.isGameRunning()
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
                && !SRE.isSkyVisible(client.player);
    }

    /** 音效的存活条件（不再需要自引用） */
    private static boolean soundAlivePredicate(boolean isInside) {
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
            stopAllSounds(soundManager);
            return;
        }

        float volume = clampVolume(SREClient.areaComponent.areasSettings.outdoorSoundVolume);
        SoundInstance instance = new MyBackgroundAmbientLoop(
                client.player,
                SoundEvent.createVariableRangeEvent(loc),
                SoundSource.MASTER,
                volume,
                t -> soundAlivePredicate(false),
                20, 10);
        switchToNewSound(soundManager, instance);
        isNowPlayingInside.set(false);
    }

    private static void playInsideSound(Minecraft client, SoundManager soundManager) {
        ResourceLocation loc = getSoundLocation(
                SREClient.areaComponent.areasSettings.sceneInsideSound,
                SREClient.areaComponent.areasSettings.customInsideSoundId,
                true);
        if (loc == null) {
            stopAllSounds(soundManager);
            return;
        }

        float volume = clampVolume(SREClient.areaComponent.areasSettings.indoorSoundVolume);
        SoundInstance instance = new MyBackgroundAmbientLoop(
                client.player,
                SoundEvent.createVariableRangeEvent(loc),
                SoundSource.MASTER,
                volume,
                t -> soundAlivePredicate(true),
                20, 10);
        switchToNewSound(soundManager, instance);
        isNowPlayingInside.set(true);
    }

    // ---------- 工具方法 ----------

    private static float clampVolume(float volume) {
        if (volume < 0)
            return 0;
        if (volume > 2)
            return 2;
        return volume;
    }

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