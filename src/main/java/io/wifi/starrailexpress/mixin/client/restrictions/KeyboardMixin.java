package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
    @WrapMethod(method = "handleDebugKeys")
    private boolean tmm$disableF3Keybinds(int key, Operation<Boolean> original) {
        if (SREClient.isInLobby) {
            return original.call(key);
        }
        if (!SREClient.isPlayerCreative()) {
            return key == 293 ? original.call(key) : false;
        } else {
            return original.call(key);
        }
    }
}
