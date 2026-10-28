package io.wifi.starrailexpress.settlement.client;

import io.wifi.starrailexpress.client.MusicBoxAmbientSound;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 只放行明确创建的提示音对象；绝不借用 ThreadLocal 放行异步现场音。 */
public final class SettlementAudio {
    private static final Set<SoundInstance> ALLOWED = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    /** 演出期间唯一的结算音乐实例；PREPARE/结束时整段停止。 */
    private static SoundInstance music;
    private SettlementAudio() { }

    public static boolean allowed(SoundInstance sound) { return ALLOWED.contains(sound); }
    static void clear() { stopSettlementMusic(); ALLOWED.clear(); }

    /**
     * 结算音乐：装配了带结算音效的音乐盒就放该音乐盒的胜利/失败曲，否则回落到默认钢琴结算音。
     * <p>只由结算演出自己播放，不是原局音轨的重放。</p>
     *
     * @param win 本地玩家本局是否胜利
     */
    static void playSettlementMusic(boolean win) {
        stopSettlementMusic();
        SoundEvent event = MusicBoxAmbientSound.equippedSettlementSound(win);
        if (event == null) event = win ? TMMSounds.UI_PIANO_WIN : TMMSounds.UI_PIANO_LOSE;
        SimpleSoundInstance sound = SimpleSoundInstance.forUI(event, 1f, 1f);
        // 长音只在 SoundEngine 校验一次，但仍留在放行集合里，避免任何重复 play 被过滤掉。
        ALLOWED.add(sound);
        music = sound;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    static void stopSettlementMusic() {
        SoundInstance old = music;
        music = null;
        if (old == null) return;
        ALLOWED.remove(old);
        Minecraft.getInstance().getSoundManager().stop(old);
    }

    static void cue(String type) {
        SoundEvent event;
        float pitch = 1;
        switch (type) {
            case "ENDER_DRAGON" -> event = SoundEvents.ENDER_DRAGON_DEATH;
            case "INTRO" -> { event = SoundEvents.NOTE_BLOCK_CHIME.value(); pitch = .75f; }
            case "PLAYER_KILL" -> { event = SoundEvents.NOTE_BLOCK_BASS.value(); pitch = .65f; }
            case "PLAYER_REVIVAL", "REVIVE" -> { event = SoundEvents.NOTE_BLOCK_BELL.value(); pitch = 1.3f; }
            case "TASK_COMPLETE" -> { event = SoundEvents.NOTE_BLOCK_PLING.value(); pitch = 1.2f; }
            case "CHANGE_ROLE" -> event = SoundEvents.NOTE_BLOCK_CHIME.value();
            default -> { event = SoundEvents.NOTE_BLOCK_HAT.value(); pitch = .9f; }
        }
        // 非位置音效，是结算专用的事件提示，不声称还原原局音轨。
        SimpleSoundInstance sound = SimpleSoundInstance.forUI(event, pitch, .32f);
        ALLOWED.add(sound);
        try { Minecraft.getInstance().getSoundManager().play(sound); }
        finally { ALLOWED.remove(sound); }
    }
}
