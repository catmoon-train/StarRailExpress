package org.agmas.noellesroles.mixin.client.decree;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 复用现有按键，不注册新键；清掉被禁止操作的点击缓存，解除后不补执行。 */
@Mixin(KeyMapping.class)
public abstract class DecreeKeysMixin {
    @Shadow private int clickCount;

    private boolean decree$blocked() {
        var client = Minecraft.getInstance();
        if (client.player == null) return false;
        Object key = this;
        var options = client.options;
        if (key == options.keyJump && DecreeClient.isRestricted(Decree.MOVEMENT)) return true;
        if (key == options.keySprint && DecreeClient.isRestricted(Decree.SPRINT)) return true;
        if (key == options.keyInventory && DecreeClient.isRestricted(Decree.INVENTORY)) return true;
        if (DecreeClient.isRestricted(Decree.HOTBAR)) {
            for (KeyMapping slot : options.keyHotbarSlots) if (key == slot) return true;
            // 中键选物也可能切换选中槽。
            if (key == options.keyPickItem) return true;
        }
        return false;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean decree$held(boolean original) {
        return original && !decree$blocked();
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void decree$click(CallbackInfoReturnable<Boolean> cir) {
        if (decree$blocked()) {
            clickCount = 0;
            cir.setReturnValue(false);
        }
    }
}
