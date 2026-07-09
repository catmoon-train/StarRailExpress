package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.client.LockControls;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LockControls.class)
public class LockControlsMixin {
    @Shadow
    public static KeyMapping poseWheelKey, paintKey, provokeKey, crawlKey;

    @Inject(method = "registerKeyMappings", at = @At("HEAD"), cancellable = true)
    private static void tmm$registerKeyMappings(CallbackInfo ci) {
        ci.cancel();
        poseWheelKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.meccha_chameleon.pose", 82, "key.categories.gameplay"));
        paintKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.meccha_chameleon.paint", InputConstants.KEY_X, "key.categories.gameplay"));
        provokeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.meccha_chameleon.provoke", 71, "key.categories.gameplay"));
        crawlKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.meccha_chameleon.crawl", 67, "key.categories.gameplay"));
    }
}
