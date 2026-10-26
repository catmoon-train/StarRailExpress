package io.wifi.starrailexpress.settlement.client;

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
    private SettlementAudio() { }

    public static boolean allowed(SoundInstance sound) { return ALLOWED.contains(sound); }
    static void clear() { ALLOWED.clear(); }

    static void cue(String type) {
        SoundEvent event;
        float pitch = 1;
        switch (type) {
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
