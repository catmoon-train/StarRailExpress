package org.agmas.noellesroles.mixin.client.roles.conductor;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.RoleNameRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RoleNameRenderer.class)
public class MasterKeyHudMixin {
    @Inject(method = "renderHud", at = @At("HEAD"))
    private static void executionerHudRenderer(Font renderer, LocalPlayer player, GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
            return;
        }
        if (Minecraft.getInstance().player.isSpectator())
            return;
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(player.level());
        if (player.getMainHandItem().is(ModItems.MASTER_KEY) && !ConfigWorldComponent.KEY.get(player.level()).masterKeyIsVisible) {
            context.pose().pushPose();
            context.pose().translate((float) context.guiWidth() / 2.0F, (float) context.guiHeight() / 2.0F + 6.0F, 0.0F);
            context.pose().scale(0.6F, 0.6F, 1.0F);
            context.setColor(1,1,1,0.5f);
            Component name = Component.translatable("tip.master_key_invisible");
            if (ConfigWorldComponent.KEY.get(player.level()).masterKeyVisibleCount != 0) {
                name = Component.translatable("tip.master_key_invisible_count", ConfigWorldComponent.KEY.get(player.level()).masterKeyVisibleCount);
            }
            context.setColor(1,1,1,1);
            context.drawString(renderer, name, -renderer.width(name) / 2, 32, CommonColors.GRAY);

            context.pose().popPose();
        }
    }
}
