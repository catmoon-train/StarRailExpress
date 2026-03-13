package pro.fazeclan.river.stupid_express.mixin.client.role.arsonist;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.client.StupidExpressClient;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.awt.*;

@Mixin(RoleNameRenderer.class)
public class ArsonistHudMixin {

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void replaceRoleHud(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        StarGameWorldComponent gameWorldComponent = StarGameWorldComponent.KEY.get(player.level());
        if (StupidExpressClient.target == null) {
            return;
        }
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, SERoles.ARSONIST) && !SREClient.isPlayerSpectatingOrCreative()) {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
            context.pose().scale(0.6f, 0.6f, 1.0f);

            DousedPlayerComponent component = DousedPlayerComponent.KEY.get(StupidExpressClient.target);
            Component status = Component.translatable("hud.stupid_express.arsonist.doused." + component.getDoused());
            context.drawString(renderer, status, -renderer.width(status) / 2, 32,component.getDoused() ?  0xfc9526 : Color.GRAY.getRGB());

            context.pose().popPose();
        }
    }

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/game/GameFunctions;isPlayerSpectatingOrCreative(Lnet/minecraft/world/entity/player/Player;)Z"))
    private static void playerRaycast(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        float range = RoleNameRenderer.getPlayerRange(player);
        HitResult line = ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof Player, range);
        StupidExpressClient.target = null;
        if (!(line instanceof EntityHitResult ehr)) {
            return;
        }
        if (!(ehr.getEntity() instanceof Player victim)) {
            return;
        }
        StupidExpressClient.target = victim;
    }

}
