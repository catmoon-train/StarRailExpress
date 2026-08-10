package io.wifi.starrailexpress.client;

import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.agmas.noellesroles.init.NRSounds;

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

public class OutsideSoundManager {
    public static final AtomicReference<SoundInstance> playingSounds = new AtomicReference<>();
    public static final AtomicBoolean isNowPlayingInside = new AtomicBoolean();
    public static final CopyOnWriteArrayList<SoundInstance> PENDING_STOP = new CopyOnWriteArrayList<>();

    public static void registerEvents() {
        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();
        ClientTickEvents.END_CLIENT_TICK.register((c) -> {
            if (!PENDING_STOP.isEmpty()) {
                if (c.player == null || c.level == null) {
                    for (var t : PENDING_STOP) {
                        if (soundManager.isActive(t))
                            soundManager.stop(t);
                    }
                    PENDING_STOP.clear();
                } else {
                    Iterator<SoundInstance> it = PENDING_STOP.iterator();
                    while (it.hasNext()) {
                        var t = it.next();
                        if (t instanceof MyBackgroundAmbientLoop mt) {
                            if (mt.isStopped()) {
                                if (soundManager.isActive(t))
                                    soundManager.stop(t);
                                it.remove();
                            }
                        } else {
                            if (soundManager.isActive(t))
                                soundManager.stop(t);
                            it.remove();
                        }
                    }
                }
            }
        });
        ClientTickEvents.START_WORLD_TICK.register((world) -> {
            if (shouldPlaySound(client)) {
                if (isInside(client)) {
                    if (playingSounds.get() == null || !isNowPlayingInside.get()) {
                        playInsideSound(client, soundManager);
                    }
                } else {
                    if (playingSounds.get() == null || isNowPlayingInside.get()) {
                        playOutsideSound(client, soundManager);
                    }
                }
            } else {
                stopNowPlayingSounds(soundManager);
            }
        });
    }

    public static void stopNowPlayingSounds(SoundManager soundManager) {
        if (playingSounds.get() == null)
            return;
        SoundInstance old = playingSounds.getAndSet(null);
        if (old != null) {
            if (soundManager.isActive(old)) {
                PENDING_STOP.add(old);
            }
        }
    }

    public static void stopNowAndPlayingNewSounds(SoundManager soundManager, SoundInstance newSoundInstance) {
        SoundInstance old = playingSounds.get();
        if (old.equals(newSoundInstance))
            return;
        if (old != null) {
            if (soundManager.isActive(old)) {
                PENDING_STOP.add(old);
            }
        }
        playingSounds.set(newSoundInstance);
        soundManager.play(newSoundInstance);
    }

    public static boolean shouldPlaySound(Minecraft client) {
        return SREClient.gameComponent != null && SREClient.areaComponent != null
                && SREClient.areaComponent.areasSettings != null
                && SREClient.areaComponent.areasSettings.haveOutsideSound
                && (GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(client.player)
                        || SREClientConfig.instance().bgsoundForSpectator);
    }

    public static boolean isInside(Minecraft client) {
        return SREClient.gameComponent != null && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(client.player)
                && SREClient.isGameRunning()
                && !SRE.isSkyVisible(client.player);
    }

    public static void playOutsideSound(Minecraft client, SoundManager soundManager) {
        ResourceLocation loc = getSoundLocation(SREClient.areaComponent.areasSettings.sceneOutsideSound,
                SREClient.areaComponent.areasSettings.customOutsideSoundId, false);
        if (loc == null) {
            stopNowPlayingSounds(soundManager);
            return;
        }
    }

    public static void playInsideSound(Minecraft client, SoundManager soundManager) {
        ResourceLocation loc = getSoundLocation(SREClient.areaComponent.areasSettings.sceneOutsideSound,
                SREClient.areaComponent.areasSettings.customOutsideSoundId, true);
        if (loc == null) {
            stopNowPlayingSounds(soundManager);
            return;
        }
        float volume = SREClient.areaComponent.areasSettings.indoorSoundVolume;
        if (volume < 0) {
            volume = 0;
        }
        if (volume > 2) {
            volume = 2;
        }
        SoundInstance soundInstance = new MyBackgroundAmbientLoop(client.player,
                SoundEvent.createVariableRangeEvent(loc), SoundSource.MASTER, volume, (t) -> shouldPlayPredicate(true),
                20, 10);
        stopNowAndPlayingNewSounds(soundManager, soundInstance);
    }

    public static boolean shouldPlayPredicate(boolean isInside) {
        Minecraft client = Minecraft.getInstance();
        return (shouldPlaySound(client) && (isInside(client) == isInside));
    }

    public static ResourceLocation getSoundLocation(BackgroundAmbienceSound soundType, String customSoundId,
            boolean isIndoor) {
        switch (soundType) {
            case circus:
                return (isIndoor ? NRSounds.CIRCUS_INDOOR : NRSounds.CIRCUS_BACKGROUND).getLocation();
            case custom:
                return ResourceLocation.tryParse(customSoundId);
            case flower_sea:
                return (NRSounds.FLOWER_OUTDOOR).getLocation();
            case indoor_music:
                return (NRSounds.MUSIC_INDOOR).getLocation();
            case sakura_moyu:
                return (NRSounds.MUSIC_SAKURA_MOYU).getLocation();
            case sand_storm:
                return (NRSounds.SAND_STORM).getLocation();
            case snow_storm:
                return (NRSounds.SNOW_STORM).getLocation();
            case train:
                return (isIndoor ? TMMSounds.AMBIENT_TRAIN_INSIDE : TMMSounds.AMBIENT_TRAIN_OUTSIDE).getLocation();
            case unwelcome_school:
                return (NRSounds.MUSIC_UNWELCOME_SCHOOL).getLocation();
            case wind:
                return (NRSounds.WIND).getLocation();
            case zenrianbanka:
                return (NRSounds.MUSIC_ZENRIANBANKA).getLocation();
            default:
                break;
        }
        return null;
    }

}
