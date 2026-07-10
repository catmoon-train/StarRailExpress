package org.agmas.noellesroles.mixin.client.general;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.TwoDimensionalCameraClientHandle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 二维视角（{@code ModEffects.TWO_DIMENSIONAL_CAMERA}）下把 OpenAL 监听者放回玩家身上。
 *
 * <p>{@code SoundEngine.updateSource} 每帧用相机位置刷新监听者，而二维视角的相机被架在玩家上方 /
 * 侧面十几到几十格外。原版音效默认衰减距离只有 16 格，于是玩家脚边的脚步声、开门声都被按相机距离
 * 衰减成静音 —— 表现为「什么都听不见」。这里只替换位置，朝向仍取相机的，左右声道因此与屏幕方向一致。
 *
 * <p>语音（simple voice chat）走的是另一套参考点，见 {@code VoicechatALSpeakerBaseMixin} /
 * {@code VoicechatFreecamUtilMixin}。
 */
@Mixin(SoundEngine.class)
public class SoundEngineListenerMixin {

    @ModifyExpressionValue(method = "updateSource", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 noellesroles$listenFromPlayer(Vec3 original) {
        Vec3 listener = TwoDimensionalCameraClientHandle.listenerPosition();
        return listener != null ? listener : original;
    }
}
