package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.sugar.Local;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.CommonColors;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {
    // changes color parameter constant
    @ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", ordinal = 1), index = 3)
    protected int renderLabelIfPresent(int color, @Local(argsOnly = true) T entity) {
        if (SREClient.isInLobby) {
            return color;
        }
        return SREClient.gameComponent.isRole(entity.getUUID(), TMMRoles.KILLER) ? CommonColors.RED : color;
    }
}
