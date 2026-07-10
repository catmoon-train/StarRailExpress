package org.agmas.noellesroles.mixin.client.roles.leather_pig;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.agmas.noellesroles.client.LeatherPigDisguiseRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 皮革噶的：第一人称也渲染自己（的猪）。
 *
 * <p>原版 renderLevel 里靠 {@code entity != camera.getEntity() || camera.isDetached()} 跳过相机所在的实体，
 * 所以第一人称下自己的模型压根不进渲染。伪装期间谎报 isDetached，玩家实体照常走 PlayerRenderer，
 * 再由 LeatherPigPlayerRenderMixin 换成猪——低头就能看到自己的猪吻。
 * 那边会把自己这只猪整体后移，猪头因此落在相机之后，不会挡住视野。
 */
@Mixin(LevelRenderer.class)
public abstract class LeatherPigSelfRenderMixin {

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;isDetached()Z"))
    private boolean noellesroles$renderSelfWhileDisguised(Camera camera, Operation<Boolean> original) {
        if (original.call(camera)) {
            return true;
        }
        return camera.getEntity() instanceof AbstractClientPlayer player
                && LeatherPigDisguiseRenderer.shouldDisguise(player);
    }
}
