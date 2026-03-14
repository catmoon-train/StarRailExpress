package org.agmas.noellesroles.mixin.client.roles.jester;

import com.mojang.blaze3d.vertex.PoseStack;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.MoodRenderer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

@Mixin(MoodRenderer.class)
public class JesterMoodRenderer {

    @Shadow public static float moodOffset;

    @Shadow public static float moodTextWidth;

    @Shadow public static float moodRender;

    @Shadow public static float moodAlpha;
    @Shadow public static Random random;
    @Unique private static final ResourceLocation JESTER_MOOD = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hud/mood_jester");

    @Inject(method = "renderKiller", at = @At("HEAD"), cancellable = true)
    private static void jesterMood(Font textRenderer, GuiGraphics context, CallbackInfo ci) {
        SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(Minecraft.getInstance().player.level());
        if (gameWorldComponent.isRole(Minecraft.getInstance().player, ModRoles.JESTER)) {
        context.pose().pushPose();
        context.pose().translate(0.0F, 3.0F * moodOffset, 0.0F);
        context.blitSprite(JESTER_MOOD, 5, 6, 14, 17);
        context.pose().popPose();
        context.pose().pushPose();
        context.pose().translate(0.0F, 10.0F * moodOffset, 0.0F);
        PoseStack var10000 = context.pose();
        var10000.translate(26.0F, (float)(8 + 9), 0.0F);
        context.pose().scale((moodTextWidth - 8.0F) * moodRender, 1.0F, 1.0F);
        context.fill(0, 0, 1, 1, ModRoles.JESTER.color() | (int)(moodAlpha * 255.0F) << 24);
        context.pose().popPose();
        ci.cancel();
        }
    }
}
