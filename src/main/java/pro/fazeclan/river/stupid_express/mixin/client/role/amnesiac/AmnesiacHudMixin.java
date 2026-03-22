package pro.fazeclan.river.stupid_express.mixin.client.role.amnesiac;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.client.StupidExpressClient;
import pro.fazeclan.river.stupid_express.constants.SERoles;

@Mixin(RoleNameRenderer.class)
public class AmnesiacHudMixin {

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void replaceRoleHud(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter,
            CallbackInfo ci) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (StupidExpressClient.targetBody == null) {
            return;
        }
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, SERoles.AMNESIAC)
                && !SREClient.isPlayerSpectatingOrCreative()) {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
            context.pose().scale(0.6f, 0.6f, 1.0f);

            Component status = Component.translatable("hud.stupid_express.amnesiac.select_body");
            context.drawString(renderer, status, -renderer.width(status) / 2, 32, 0x9baae8);

            context.pose().popPose();
        }
    }

    @Inject(method = "renderHud", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/game/GameUtils;isPlayerSpectatingOrCreative(Lnet/minecraft/world/entity/player/Player;)Z"))
    private static void playerBodyRaycast(Font renderer, LocalPlayer player, GuiGraphics context,
            DeltaTracker tickCounter, CallbackInfo ci) {
        float range = RoleNameRenderer.getPlayerRange(player);
        HitResult line = ProjectileUtil.getHitResultOnViewVector(player, entity -> entity instanceof PlayerBodyEntity,
                range);
        StupidExpressClient.targetBody = null;
        if (!(line instanceof EntityHitResult ehr)) {
            return;
        }
        if (!(ehr.getEntity() instanceof PlayerBodyEntity victim)) {
            return;
        }
        StupidExpressClient.targetBody = victim;
    }

}
