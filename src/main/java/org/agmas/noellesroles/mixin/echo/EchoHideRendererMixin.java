package org.agmas.noellesroles.mixin.echo;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.content.EchoHideService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 草丛隐身的渲染裁剪：**按观察者**决定要不要画这名玩家。
 *
 * <p>为什么不复用原版 {@code Invisible}：那是全局标记，打上以后所有人都看不见，
 * 表达不了「同一目标对草丛外的截流者不可见、对队友可见」。所以这里在
 * {@code EntityRenderer.shouldRender} 的 HEAD 直接按客户端已知的双方阵营与站位裁剪。
 *
 * <p>只影响回声对局、只影响玩家实体、旁观者与创造模式不裁剪，
 * 因此不会波及其它模式的隐身职业或调试查图。
 */
@Mixin(EntityRenderer.class)
public class EchoHideRendererMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void echoHideBushShouldRender(Entity entity, Frustum frustum, double x, double y, double z,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!(entity instanceof Player target)) return;
        Minecraft client = Minecraft.getInstance();
        Player self = client.player;
        if (self == null || self == target) return;
        var level = client.level;
        if (level == null) return;

        // 只在回声对局里生效，避免影响其它模式的渲染。
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        if (game == null || !game.isRunning()
                || !game.getGameMode().identifier.equals(EchoBootstrap.ID)) return;

        if (EchoHideService.hiddenFor(self, target)) {
            cir.setReturnValue(false);
        }
    }
}
