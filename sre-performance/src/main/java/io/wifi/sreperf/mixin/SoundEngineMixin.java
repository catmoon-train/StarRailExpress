package io.wifi.sreperf.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.sreperf.CaptureController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
    @WrapMethod(method = "tick")
    private void sreperf$sound(boolean paused, Operation<Void> original) {
        long token = CaptureController.begin("sound_tick");
        try { original.call(paused); }
        finally { CaptureController.end("sound_tick", token, 0); }
    }

    @WrapMethod(method = "play")
    private void sreperf$play(SoundInstance sound, Operation<Void> original) {
        long token = CaptureController.begin("sound_play");
        try { original.call(sound); }
        finally { CaptureController.end("sound_play", token, 0); }
    }
}
