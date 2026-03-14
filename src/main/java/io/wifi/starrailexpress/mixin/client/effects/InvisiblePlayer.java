package io.wifi.starrailexpress.mixin.client.effects;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 处理隐身渲染
 */
@Mixin(EntityRenderer.class)
public class InvisiblePlayer {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hideInvisiblePlayer(Entity entity, Frustum frustum, double x, double y, double z,
            CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player player) {
            if (player.hasEffect(MobEffects.INVISIBILITY))
                // 傩面游魂状态下完全隐身，其他玩家看不到
                cir.setReturnValue(false);
        }
    }
}