package pro.fazeclan.river.stupid_express.mixin.client.role.necromancer;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.client.StupidExpressClient;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.necromancer.cca.NecromancerComponent;

@Mixin(RoleNameRenderer.class)
public class NecromancerHudMixin {

    @Inject(method = "renderHud", at = @At("TAIL"))
    private static void replaceRoleHud(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (StupidExpressClient.targetBody == null) {
            return;
        }
        var p = Minecraft.getInstance().player;
        if (gameWorldComponent.isRole(p, SERoles.NECROMANCER) && !SREClient.isPlayerSpectatingOrCreative()) {
            context.pose().pushPose();
            context.pose().translate(context.guiWidth() / 2.0f, context.guiHeight() / 2.0f + 6.0f, 0.0f);
            context.pose().scale(0.6f, 0.6f, 1.0f);

            Component status = Component.translatable("hud.stupid_express.necromancer.possible_revive");

            NecromancerComponent nc = NecromancerComponent.KEY.get(player.level());
            if (nc.getAvailableRevives() < 1) {
                status = Component.translatable("hud.stupid_express.necromancer.no_possible_revive");
            }
            SREAbilityPlayerComponent cooldown = SREAbilityPlayerComponent.KEY.get(p);
            if (cooldown.hasCooldown()) {
                status = Component.translatable("hud.stupid_express.necromancer.cooldown", cooldown.getCooldown()/20);
            }
            context.drawString(renderer, status, -renderer.width(status) / 2, 32, 0x9457ff);

            context.pose().popPose();
        }
    }

}