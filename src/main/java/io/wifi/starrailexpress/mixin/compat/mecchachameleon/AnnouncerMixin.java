package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.game.Announcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Announcer.Config.class)
public class AnnouncerMixin {
    @Shadow
    public List<String> messages;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        messages = new ArrayList<>();
    }
}
