package cn.zbx1425.minopp.fabric.mixin;

import cn.zbx1425.minopp.MinoClient;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayer.class)
public class AbstractClientPlayerMixin {

    @ModifyReturnValue(method = "getFieldOfViewModifier", at = @At("RETURN"))
    public float getFieldOfViewModifier(float original) {
        return original * (float) MinoClient.globalFovModifier;
    }
}
