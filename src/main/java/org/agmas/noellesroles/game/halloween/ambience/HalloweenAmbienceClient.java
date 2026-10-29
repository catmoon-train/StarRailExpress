package org.agmas.noellesroles.game.halloween.ambience;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;

/** 仅在活动地图局内持有一个流式循环；不每 tick 重播，也不停止唱片或语音。 */
@Environment(EnvType.CLIENT)
public final class HalloweenAmbienceClient {
    private static final HalloweenAmbienceState STATE = new HalloweenAmbienceState();
    private static boolean registered;
    private static TownMusic music;

    private HalloweenAmbienceClient() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(HalloweenAmbienceClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(client));
    }

    public static boolean active(ClientLevel level) {
        Minecraft client = Minecraft.getInstance();
        return registered && level != null && client.level == level
                && HalloweenAmbienceState.enabled(HalloweenRuntime.isMapEnabled(level),
                        HalloweenRuntime.isEnabled(level), client.player != null);
    }

    /** MusicManager 的门控只管原版随机 BGM，不拦截 SoundManager 或其它声音分类。 */
    public static boolean suppressVanillaMusic() {
        return active(Minecraft.getInstance().level);
    }

    private static void tick(Minecraft client) {
        boolean audible = client.options.getSoundSourceVolume(SoundSource.MUSIC) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        var change = STATE.update(client.level, active(client.level), audible);
        if (change.stop()) stopSound(client);
        if (change.start()) {
            client.getMusicManager().stopPlaying();
            music = new TownMusic();
            client.getSoundManager().play(music);
        }
    }

    /** SoundEngine.reload 的 HEAD 调用；F3+T / F3+S 后下一 tick 使用新资源重新开流。 */
    public static void onSoundReload() {
        if (registered) stop(Minecraft.getInstance());
    }

    private static void stop(Minecraft client) {
        stopSound(client);
        STATE.reset();
    }

    private static void stopSound(Minecraft client) {
        if (music != null) {
            client.getSoundManager().stop(music);
            music = null;
        }
    }

    private static final class TownMusic extends AbstractSoundInstance {
        private TownMusic() {
            super(HalloweenAmbience.TOWN_MUSIC, SoundSource.MUSIC, RandomSource.create());
            volume = 1.0F;
            pitch = 1.0F;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
        }
    }
}
